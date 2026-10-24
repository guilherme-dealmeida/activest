package com.activest.domain.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.domain.notification.NotificationDTOs.Item;
import com.activest.domain.user.UserRepository;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A caixa da pessoa logada: lista, contador, abrir (R2), estado em lote e status (dispensar/reabrir). As de
 * administrador só aparecem para quem tem {@code is_admin}; para os demais, 404 (mesmo critério do recurso de outro
 * dono).
 */
@Service
@RequiredArgsConstructor
public class NotificationBoxService {

    static final int PAGE_SIZE = 50;

    private final NotificationRepository repository;
    private final NotificationService notifications;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public NotificationDTOs.Page list(UUID me, String folder, String audience, String category, String status,
                                      boolean unreadOnly, int page) {
        boolean admin = isAdmin(me);
        NotificationAudience aud = parse(NotificationAudience.class, audience);
        if (aud == NotificationAudience.ADMIN && !admin) {
            return new NotificationDTOs.Page(List.of(), page, PAGE_SIZE, 0);
        }
        boolean anyStatus = status == null || status.isBlank();
        boolean informative = "INFORMATIVA".equalsIgnoreCase(status);
        NotificationStatus st = anyStatus || informative ? null : parse(NotificationStatus.class, status);
        var result = repository.findBox(me, admin, "ARCHIVED".equalsIgnoreCase(folder), aud,
            parse(NotificationCategory.class, category), anyStatus, informative, st, unreadOnly,
            PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        return new NotificationDTOs.Page(result.getContent().stream().map(this::toItem).toList(),
            result.getNumber(), PAGE_SIZE, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotificationDTOs.Summary summary(UUID me) {
        long unread = 0;
        long adminUnread = 0;
        for (Object[] row : repository.countUnread(me, isAdmin(me))) {
            long n = ((Number) row[1]).longValue();
            if (row[0] == NotificationAudience.ADMIN) {
                adminUnread = n;
            } else {
                unread = n;
            }
        }
        return new NotificationDTOs.Summary(unread + adminUnread, adminUnread);
    }

    @Transactional(readOnly = true)
    public Item get(UUID me, UUID id) {
        return toItem(visible(me, id));
    }

    /** Abrir é o único caminho para "lida" (R2). */
    @Transactional
    public Item open(UUID me, UUID id) {
        boolean admin = isAdmin(me);
        visible(me, id);
        notifications.applyState(List.of(id), me, admin, NotificationService.StateAction.READ);
        return toItem(visible(me, id));
    }

    @Transactional
    public NotificationDTOs.StateResult applyState(UUID me, NotificationDTOs.StateRequest req) {
        if (req == null || req.action() == null) {
            throw new BusinessException("Informe a ação.");
        }
        return new NotificationDTOs.StateResult(notifications.applyState(req.ids(), me, isAdmin(me), req.action()));
    }

    @Transactional
    public Item changeStatus(UUID me, UUID id, NotificationDTOs.StatusRequest req) {
        if (req == null || req.action() == null) {
            throw new BusinessException("Informe a ação.");
        }
        notifications.changeStatus(id, me, isAdmin(me), req.action() == NotificationDTOs.StatusAction.DISMISS);
        return toItem(visible(me, id));
    }

    // ------------------------------------------------------------------ apoio

    private NotificationView visible(UUID me, UUID id) {
        return repository.findVisible(id, me, isAdmin(me))
            .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada"));
    }

    private boolean isAdmin(UUID me) {
        return users.findById(me).map(u -> u.isAdmin()).orElse(false);
    }

    private Item toItem(NotificationView v) {
        Notification n = v.notification();
        return new Item(n.getId(), n.getAudience().name(), n.getKind().name(), n.getCategory().name(),
            n.getSeverity().name(), n.getTitle(), n.getBody(), n.getRefType(), n.getRefId(), json(n.getPayload()),
            n.getStatus() == null ? null : n.getStatus().name(), n.getStatusAt(), v.read(), v.archived(),
            n.getCreatedAt());
    }

    private JsonNode json(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Filtro inválido: " + value);
        }
    }
}
