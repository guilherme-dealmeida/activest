package com.activest.domain.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * A central de notificações (docs/planos/PLANO_NOTIFICACOES.md). Os produtores (app-api e adm-api) só chamam
 * {@link #notify}; a repetição é barrada pela chave (R10) no próprio banco ({@code ON CONFLICT DO NOTHING}), então
 * uma rotina pode rodar quantas vezes quiser no dia sem duplicar nada.
 *
 * <p>Uma falha aqui nunca derruba o produtor: avisar é efeito colateral (log e segue).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final NotificationRepository repository;
    private final NotificationStateRepository states;

    /**
     * Cria a mensagem se a chave ainda não existir. Transação própria: o aviso não some se o produtor desfizer a dele
     * depois, nem derruba o produtor se falhar.
     *
     * @return o id criado; vazio quando a chave já existia (ou deu erro)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> notify(NotificationDraft d) {
        try {
            NotificationKind kind = d.kind();
            if (kind.audience() == NotificationAudience.USER && d.userId() == null) {
                throw new IllegalArgumentException("mensagem de usuario sem usuario: " + kind);
            }
            UUID userId = kind.audience() == NotificationAudience.USER ? d.userId() : null;
            List<UUID> ids = jdbc.queryForList("""
                INSERT INTO notifications (id, audience, user_id, kind, category, severity, title, body, ref_type,
                                           ref_id, payload, status, status_at, dedup_key, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                RETURNING id
                """, UUID.class,
                kind.audience().name(), userId, kind.name(), kind.category().name(), kind.severity().name(),
                truncate(d.title(), 200), d.body(), d.refType(), d.refId(), payload(d),
                kind.pending() ? NotificationStatus.NAO_RESOLVIDA.name() : null,
                kind.pending() ? now() : null,
                d.dedupKey() == null ? null : truncate(kind.name() + ":" + d.dedupKey(), 200),
                now());
            return ids.stream().findFirst();
        } catch (RuntimeException e) {
            log.warn("Notificacao {} nao gravada: {}", d.kind(), e.getMessage());
            return Optional.empty();
        }
    }

    /** A chave já foi usada por este tipo/dono (informativa: alguma vez; pendência: qualquer status). */
    @Transactional(readOnly = true)
    public boolean exists(NotificationKind kind, UUID userId, String dedupKey) {
        return repository.existsByKindAndUserIdAndDedupKey(kind, ownerOf(kind, userId), kind.name() + ":" + dedupKey);
    }

    /** O sistema viu que o assunto acabou: a pendência da chave fica RESOLVIDA (R6). */
    @Transactional
    public int resolve(NotificationKind kind, UUID userId, String dedupKey) {
        String key = kind.name() + ":" + dedupKey;
        List<UUID> ids = repository.findOpen(kind, ownerOf(kind, userId)).stream()
            .filter(n -> key.equals(n.getDedupKey()))
            .map(Notification::getId).toList();
        return ids.isEmpty() ? 0 : repository.resolveIds(ids, now());
    }

    /**
     * Resolve as pendências abertas do tipo cuja chave não está mais em {@code stillOpen} — a conferência que roda de
     * novo e não acha mais o problema. {@code userId} nulo nas de administrador.
     */
    @Transactional
    public int resolveAllExcept(NotificationKind kind, UUID userId, Collection<String> stillOpen) {
        Set<String> keep = new HashSet<>();
        stillOpen.forEach(k -> keep.add(kind.name() + ":" + k));
        List<UUID> ids = repository.findOpen(kind, ownerOf(kind, userId)).stream()
            .filter(n -> n.getDedupKey() == null || !keep.contains(n.getDedupKey()))
            .map(Notification::getId).toList();
        return ids.isEmpty() ? 0 : repository.resolveIds(ids, now());
    }

    /** Resolve uma pendência pelo id (o produtor guardou a referência). */
    @Transactional
    public int resolveIds(Collection<UUID> ids) {
        return ids.isEmpty() ? 0 : repository.resolveIds(ids, now());
    }

    /** Pendências abertas (não resolvidas ou dispensadas) do tipo, de qualquer dono. */
    @Transactional(readOnly = true)
    public List<Notification> openOf(NotificationKind kind) {
        return repository.findOpenOfAnyone(kind);
    }

    /** A chave sem o prefixo do tipo, como o produtor a montou. */
    public static String keyOf(Notification n) {
        String k = n.getDedupKey();
        String prefix = n.getKind().name() + ":";
        return k != null && k.startsWith(prefix) ? k.substring(prefix.length()) : k;
    }

    // ------------------------------------------------------------------ status pela pessoa (R6)

    /** Dispensar ou reabrir uma pendência visível para a pessoa. Informativa não tem status: nada muda. */
    @Transactional
    public Notification changeStatus(UUID id, UUID me, boolean admin, boolean dismiss) {
        NotificationView v = repository.findVisible(id, me, admin)
            .orElseThrow(() -> new com.activest.exception.ResourceNotFoundException("Notificação não encontrada"));
        Notification n = v.notification();
        if (n.getStatus() == null) {
            throw new com.activest.exception.BusinessException("Mensagem informativa não tem status.");
        }
        if (dismiss && n.getStatus() == NotificationStatus.NAO_RESOLVIDA) {
            n.setStatus(NotificationStatus.DISPENSADA);
        } else if (!dismiss && n.getStatus() == NotificationStatus.DISPENSADA) {
            n.setStatus(NotificationStatus.NAO_RESOLVIDA);
        } else {
            return n;
        }
        n.setStatusAt(now());
        n.setStatusBy(me);
        return repository.save(n);
    }

    // ------------------------------------------------------------------ estado por pessoa (R1–R5)

    public enum StateAction { READ, UNREAD, ARCHIVE, UNARCHIVE, DELETE }

    /** Aplica a ação às mensagens visíveis entre {@code ids}; devolve quantas mudaram. */
    @Transactional
    public int applyState(Collection<UUID> ids, UUID me, boolean admin, StateAction action) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<UUID> visible = repository.filterVisible(new HashSet<>(ids), me, admin);
        if (visible.isEmpty()) {
            return 0;
        }
        Map<UUID, NotificationState> current = new HashMap<>();
        states.findByUserIdAndNotificationIdIn(me, visible).forEach(s -> current.put(s.getNotificationId(), s));
        LocalDateTime now = now();
        List<NotificationState> changed = new ArrayList<>();
        for (UUID id : visible) {
            NotificationState s = current.getOrDefault(id, new NotificationState(id, me));
            switch (action) {
                case READ -> { if (s.getReadAt() == null) s.setReadAt(now); }
                case UNREAD -> s.setReadAt(null);
                case ARCHIVE -> { if (s.getArchivedAt() == null) s.setArchivedAt(now); }
                case UNARCHIVE -> s.setArchivedAt(null);
                case DELETE -> { if (s.getDeletedAt() == null) s.setDeletedAt(now); }
            }
            changed.add(s);
        }
        states.saveAll(changed);
        return changed.size();
    }

    // ------------------------------------------------------------------ apoio

    private static UUID ownerOf(NotificationKind kind, UUID userId) {
        return kind.audience() == NotificationAudience.USER ? userId : null;
    }

    private String payload(NotificationDraft d) {
        if (d.lines().isEmpty() && d.actions().isEmpty() && d.data().isEmpty()) {
            return null;
        }
        Map<String, Object> p = new LinkedHashMap<>();
        if (!d.lines().isEmpty()) p.put("lines", d.lines());
        if (!d.actions().isEmpty()) p.put("actions", d.actions());
        if (!d.data().isEmpty()) p.put("data", d.data());
        try {
            return objectMapper.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Mesmo relógio dos outros carimbos (created_at das entidades): o do servidor; a tela converte. */
    static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** Hoje no fuso da B3 — o "dia" das chaves de repetição e das rotinas de D0. */
    public static java.time.LocalDate today() {
        return java.time.LocalDate.now(ZONE);
    }
}
