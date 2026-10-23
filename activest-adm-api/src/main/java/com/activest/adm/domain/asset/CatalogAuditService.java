package com.activest.adm.domain.asset;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.PriceSource;

import com.activest.domain.portfolio.OpenPositions;
import com.activest.adm.integration.brapi.BrApiClient;
import com.activest.adm.integration.brapi.BrApiException;
import com.activest.adm.integration.brapi.BrApiRename;
import com.activest.adm.integration.brapi.BrApiTicker;
import com.activest.adm.domain.notice.AdminNotices;
import com.activest.domain.notification.NotificationKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Conferencia do catalogo contra a lista oficial da BRAPI (docs/planos/PLANO_BRAPI.md, B23).
 *
 * <p><b>Nao grava nada no catalogo.</b> Cada diferenca vira uma linha de log
 * (WARN) e uma mensagem de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7); a rodada seguinte que nao acha
 * mais a diferenca resolve a mensagem. Tres conferencias:</p>
 * <ul>
 *   <li><b>ticker novo na B3</b> — esta na lista da BRAPI e nao no catalogo;</li>
 *   <li><b>ticker sumido</b> — ativo do catalogo que nao esta na lista E cuja
 *   cotacao volta 404 (criterio da secao 2.1). Ausente da lista mas com
 *   cotacao e papel sem negocio, nao deslistado: nada a dizer;</li>
 *   <li><b>ticker renomeado</b> — o ticker antigo de um renome da BRAPI ainda
 *   esta ativo no catalogo. O catalogo nao e trocado sozinho.</li>
 * </ul>
 *
 * <p>Custo: a lista e os renomes nao gastam cota; so a confirmacao do sumido
 * gasta, uma requisicao por candidato.</p>
 *
 * <p>Tickers conhecidos que ficaram de fora de proposito (BRAX, ESUD11,
 * ABCB10L e os 38 "nao ETF" da planilha) vem de
 * {@code activest.catalog-audit.ignored} e nao sao anunciados toda semana.</p>
 */
@Slf4j
@Service
public class CatalogAuditService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Subtipo da BRAPI → tipo do Activest. */
    private static final Map<String, String> SUBTYPES = Map.of(
            "stock", "ACAO", "unit", "ACAO", "fii", "FII", "etf", "ETF", "bdr", "BDR");

    private final AssetRepository assetRepository;
    private final OpenPositions openPositions;
    private final BrApiClient brApiClient;
    private final Set<String> ignored;
    private final AdminNotices notices;

    public CatalogAuditService(AssetRepository assetRepository, OpenPositions openPositions, BrApiClient brApiClient,
                               @Value("${activest.catalog-audit.ignored:}") List<String> ignored,
                               AdminNotices notices) {
        this.assetRepository = assetRepository;
        this.openPositions = openPositions;
        this.brApiClient = brApiClient;
        this.notices = notices;
        this.ignored = ignored.stream().map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
    }

    public record AuditResult(int newTickers, int missingTickers, int renamed, boolean failed) {
    }

    public synchronized AuditResult run(String reason) {
        Map<String, String> official = new HashMap<>();
        try {
            for (Map.Entry<String, String> e : SUBTYPES.entrySet()) {
                for (BrApiTicker t : brApiClient.fetchTickers(e.getKey())) {
                    if (t.symbol() != null) {
                        official.put(t.symbol().trim().toUpperCase(), e.getValue());
                    }
                }
            }
        } catch (BrApiException e) {
            log.error("Conferencia do catalogo ({}) falhou ao buscar a lista da BRAPI: {}", reason, e.getMessage());
            notices.sourceUnavailable("Conferência do catálogo", "BRAPI", e.getMessage());
            return new AuditResult(0, 0, 0, true);
        }

        List<Asset> catalog = assetRepository.findActiveWithType();
        Set<String> allTickers = assetRepository.findAll().stream().map(a -> a.getTicker().toUpperCase())
                .collect(Collectors.toSet());
        Set<Long> held = new HashSet<>();
        openPositions.assetsWithShares(PriceSource.BRAPI).forEach(a -> held.add(a.getId()));

        // 1. Novos
        int news = 0;
        List<String> added = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : official.entrySet()) {
            if (!allTickers.contains(e.getKey()) && !ignored.contains(e.getKey())) {
                news++;
                added.add(e.getKey() + " (" + e.getValue() + ")");
                log.warn("Catalogo: ticker novo na B3 — {} ({}) apareceu na BRAPI e nao esta no catalogo", e.getKey(), e.getValue());
            }
        }
        // Uma mensagem por rodada com a lista (a semana pode trazer dezenas); a da rodada anterior fica resolvida.
        String round = String.valueOf(com.activest.domain.notification.NotificationService.today());
        if (!added.isEmpty()) {
            java.util.Collections.sort(added);
            notices.pending(NotificationKind.CATALOGO_TICKER_NOVO, round,
                news + (news == 1 ? " ticker novo na B3" : " tickers novos na B3"),
                "Estão na lista oficial da BRAPI e não estão no catálogo. Para cadastrar, use a carga do catálogo; para "
                    + "ignorar de propósito, ponha em activest.catalog-audit.ignored.", added);
        }
        notices.resolveAllExcept(NotificationKind.CATALOGO_TICKER_NOVO, added.isEmpty() ? List.of() : List.of(round));

        // 2. Sumidos: fora da lista (o fracionario confere o papel inteiro) e com cotacao 404
        int missing = 0;
        Set<String> vanished = new HashSet<>();
        boolean vanishedComplete = brApiClient.hasToken();
        for (Asset a : catalog) {
            String type = a.getAssetType().getName();
            if (!SUBTYPES.containsValue(type)) {
                continue;
            }
            String ticker = a.getTicker().toUpperCase();
            String base = ticker.endsWith("F") && ticker.length() > 5 ? ticker.substring(0, ticker.length() - 1) : ticker;
            if (official.containsKey(ticker) || official.containsKey(base) || ignored.contains(ticker)) {
                continue;
            }
            if (!brApiClient.hasToken()) {
                continue;
            }
            try {
                brApiClient.fetchQuote(ticker);
            } catch (BrApiException e) {
                if (e.isNotFound()) {
                    missing++;
                    vanished.add(ticker);
                    log.warn("Catalogo: ticker sumido — {} nao esta na lista oficial e a cotacao da 404{}. Candidato a inativo (criterio 2.1).",
                            ticker, held.contains(a.getId()) ? " (TEM POSICAO ABERTA)" : "");
                    notices.assetPending(NotificationKind.CATALOGO_TICKER_SUMIDO, a, ticker,
                            ticker + " — sumiu da B3" + (held.contains(a.getId()) ? " (há posição aberta)" : ""),
                            ticker + " não está na lista oficial e a cotação responde 404: candidato a inativo no "
                                    + "catálogo (critério 2.1). O catálogo não é mudado sozinho.");
                } else if (e.isAuthOrPlan() || e.isRateLimited()) {
                    log.error("Conferencia do catalogo: confirmacao dos sumidos interrompida: {}", e.getMessage());
                    notices.sourceUnavailable("Conferência do catálogo", "BRAPI", e.getMessage());
                    vanishedComplete = false;
                    break;
                } else {
                    // Nao conferido agora: a mensagem aberta dele (se houver) continua.
                    vanished.add(ticker);
                    log.warn("Catalogo: nao foi possivel conferir a cotacao de {}: {}", ticker, e.getMessage());
                }
            }
        }

        if (vanishedComplete) {
            notices.resolveAllExcept(NotificationKind.CATALOGO_TICKER_SUMIDO, vanished);
        }

        // 3. Renomeados
        int renamed = 0;
        try {
            Map<String, Asset> active = new HashMap<>();
            catalog.forEach(a -> active.put(a.getTicker().toUpperCase(), a));
            Set<String> renames = new HashSet<>();
            for (BrApiRename r : brApiClient.fetchRenames()) {
                if (r.oldSymbol() != null && active.containsKey(r.oldSymbol().toUpperCase())) {
                    renamed++;
                    log.warn("Catalogo: ticker renomeado — {} agora e {}{} (o ticker do catalogo nao e trocado sozinho)",
                            r.oldSymbol(), r.newSymbol(), since(r.effectiveDate()));
                    String key = renameKey(r.oldSymbol(), r.newSymbol());
                    renames.add(key);
                    notices.assetPending(NotificationKind.CATALOGO_TICKER_RENOMEADO,
                            active.get(r.oldSymbol().toUpperCase()), key,
                            r.oldSymbol().toUpperCase() + " agora é " + r.newSymbol().toUpperCase(),
                            "A B3 renomeou o papel" + since(r.effectiveDate()) + ". O ticker do catálogo não é trocado "
                                    + "sozinho: decida a troca manualmente.");
                }
            }
            notices.resolveAllExcept(NotificationKind.CATALOGO_TICKER_RENOMEADO, renames);
        } catch (BrApiException e) {
            log.warn("Conferencia do catalogo: renomes indisponiveis: {}", e.getMessage());
        }

        log.info("Conferencia do catalogo ({}) concluida: {} novos, {} sumidos, {} renomeados, RateLimit-Remaining={}",
                reason, news, missing, renamed, brApiClient.lastRateLimitRemaining());
        return new AuditResult(news, missing, renamed, false);
    }

    /** Chave do renome (a mesma do fechamento [A1], para as duas rotinas não repetirem a mensagem). */
    public static String renameKey(String oldSymbol, String newSymbol) {
        return oldSymbol.toUpperCase() + "->" + (newSymbol == null ? "?" : newSymbol.toUpperCase());
    }

    private static String since(String isoDate) {
        try {
            return isoDate == null ? "" : " desde " + LocalDate.parse(isoDate.substring(0, 10)).format(BR);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
