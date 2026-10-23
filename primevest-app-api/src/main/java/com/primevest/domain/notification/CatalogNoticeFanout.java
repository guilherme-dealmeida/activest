package com.primevest.domain.notification;

import com.primevest.domain.portfolio.OpenPositions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Leva ao usuário o aviso de catálogo que pega a posição dele (docs/planos/PLANO_NOTIFICACOES.md, 5.7 e D2): ticker
 * sumido, renomeado ou sem cotação. A adm-api grava o aviso de administrador; aqui, quem tem cotas do ativo ganha
 * a pendência {@link NotificationKind#POSICAO_TICKER}, resolvida quando o aviso de administrador for resolvido (ou a
 * posição deixar de existir).
 *
 * <p>Roda na rotina de abertura do dia e depois de cada "catálogo mudou" de fechamento — idempotente pela chave
 * (aviso de administrador + posição).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogNoticeFanout {

    static final List<NotificationKind> SOURCES = List.of(NotificationKind.CATALOGO_TICKER_SUMIDO,
        NotificationKind.CATALOGO_TICKER_RENOMEADO, NotificationKind.CATALOGO_SEM_COTACAO);

    private final NotificationService notifications;
    private final OpenPositions openPositions;

    public void run() {
        try {
            fanOut();
        } catch (RuntimeException e) {
            log.warn("Avisos de catalogo para as posicoes: {}", e.getMessage());
        }
    }

    private void fanOut() {
        Map<Long, List<Notification>> byAsset = new HashMap<>();
        for (NotificationKind kind : SOURCES) {
            for (Notification n : notifications.openOf(kind)) {
                // dispensado pelo administrador continua valendo para quem tem a posicao
                if (!"ASSET".equals(n.getRefType()) || n.getRefId() == null) {
                    continue;
                }
                byAsset.computeIfAbsent(Long.valueOf(n.getRefId()), k -> new ArrayList<>()).add(n);
            }
        }

        Set<String> keep = new HashSet<>();
        for (OpenPositions.Holding h : openPositions.holdersOf(byAsset.keySet())) {
            for (Notification admin : byAsset.getOrDefault(h.asset().getId(), List.of())) {
                String key = admin.getId() + ":" + h.portfolioAssetId();
                keep.add(key);
                String ticker = h.asset().getTicker();
                notifications.notify(NotificationDraft.of(NotificationKind.POSICAO_TICKER).user(h.userId())
                    .title(admin.getTitle())
                    .body(text(admin.getKind(), ticker))
                    .ref("ASSET", h.asset().getId()).key(key)
                    .data("ticker", ticker)
                    .link("Ver carteira", "/portfolio")
                    .build());
            }
        }

        // Aviso de administrador resolvido (ou posição zerada): a pendência do usuário também.
        List<UUID> resolve = new ArrayList<>();
        for (Notification n : notifications.openOf(NotificationKind.POSICAO_TICKER)) {
            String key = NotificationService.keyOf(n);
            if (key == null || !keep.contains(key)) {
                resolve.add(n.getId());
            }
        }
        notifications.resolveIds(resolve);
    }

    private static String text(NotificationKind kind, String ticker) {
        return switch (kind) {
            case CATALOGO_TICKER_SUMIDO -> ticker + " saiu da lista oficial da B3 e a cotação não responde mais. "
                + "O preço da sua posição fica parado até o catálogo ser ajustado. Se houve incorporação, fechamento "
                + "de capital ou troca de ticker, confira o fato relevante da empresa.";
            case CATALOGO_TICKER_RENOMEADO -> "O papel " + ticker + " mudou de ticker na B3. O catálogo do PrimeVest "
                + "ainda não foi trocado; até lá o preço da sua posição pode ficar parado.";
            case CATALOGO_SEM_COTACAO -> "A cotação de " + ticker + " não respondeu no fechamento: o preço da sua "
                + "posição fica parado até ela voltar.";
            default -> "Há um aviso de catálogo sobre " + ticker + ", um ativo da sua carteira.";
        };
    }
}
