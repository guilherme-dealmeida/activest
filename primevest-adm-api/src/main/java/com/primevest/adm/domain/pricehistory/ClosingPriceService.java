package com.primevest.adm.domain.pricehistory;

import com.primevest.domain.pricehistory.AssetPriceHistory;
import com.primevest.domain.pricehistory.AssetPriceHistoryRepository;
import com.primevest.domain.pricehistory.PriceHistorySource;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.primevest.domain.portfolio.OpenPositions;
import com.primevest.adm.integration.brapi.BrApiCandle;
import com.primevest.adm.integration.brapi.BrApiClient;
import com.primevest.adm.integration.brapi.BrApiException;
import com.primevest.adm.integration.brapi.BrApiHistory;
import com.primevest.adm.integration.coingecko.CoinGeckoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rotina de fechamento: preco de fechamento e historico diario (PLANO_BRAPI.md,
 * secao 3, F2 e F4).
 *
 * <p><b>B3 (BRAPI).</b> So os ativos em carteira, 1 por chamada, em sequencia.
 * Em vez da cotacao, usa o historico diario ({@code range=5d}): mesmo custo (1
 * requisicao por ativo) e ja traz abertura, maxima, minima, fechamento e volume
 * dos ultimos pregoes. Cada pregao e gravado (ou atualizado) em
 * asset_price_history e o ultimo fechamento vai para assets.current_price.</p>
 * <ul>
 *   <li>Ativo sem historico gravado: carga inicial com {@code range=3mo} (F4).</li>
 *   <li>Faltando mais pregoes do que o 5d cobre: {@code 1mo} ou {@code 3mo},
 *   o limite do plano gratuito.</li>
 *   <li>404: o ativo e pulado e registrado. 401/403: a rodada para — repetir
 *   nao adianta.</li>
 *   <li>Ticker renomeado ({@code changed}): so registra antigo → novo; o
 *   catalogo nao e trocado sozinho.</li>
 * </ul>
 *
 * <p><b>Cripto (CoinGecko).</b> Uma vez ao dia, no mesmo horario, todos os
 * dias: o preco em reais das moedas em carteira, gravado como fechamento do dia
 * (B12). Uma chamada para todas.</p>
 *
 * <p>Grava ativo a ativo, cada um na sua transacao de banco: um erro no meio
 * nao desfaz o que ja foi gravado, e nenhuma transacao fica aberta enquanto a
 * rotina espera a rede.</p>
 */
@Slf4j
@Service
public class ClosingPriceService {

    public static final ZoneId B3_ZONE = ZoneId.of("America/Sao_Paulo");
    /** Horario do fechamento (19:00 — a B3 fecha 18:00 e a BRAPI atualiza a cada 30 min). */
    public static final LocalTime CLOSING_TIME = LocalTime.of(19, 0);
    /** Erros de rede seguidos que encerram a rodada: a rede caiu, nao o ativo. */
    private static final int MAX_NETWORK_FAILURES = 3;

    private final AssetRepository assetRepository;
    private final AssetPriceHistoryRepository historyRepository;
    private final OpenPositions openPositions;
    private final BrApiClient brApiClient;
    private final CoinGeckoClient coinGeckoClient;
    private final CatalogChangePublisher catalogPublisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ClosingPriceService(AssetRepository assetRepository, AssetPriceHistoryRepository historyRepository,
                               OpenPositions openPositions, BrApiClient brApiClient, CoinGeckoClient coinGeckoClient,
                               CatalogChangePublisher catalogPublisher, PlatformTransactionManager transactionManager) {
        this(assetRepository, historyRepository, openPositions, brApiClient, coinGeckoClient, catalogPublisher,
                transactionManager, Clock.system(B3_ZONE));
    }

    ClosingPriceService(AssetRepository assetRepository, AssetPriceHistoryRepository historyRepository,
                        OpenPositions openPositions, BrApiClient brApiClient, CoinGeckoClient coinGeckoClient,
                        CatalogChangePublisher catalogPublisher, PlatformTransactionManager transactionManager,
                        Clock clock) {
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
        this.openPositions = openPositions;
        this.brApiClient = brApiClient;
        this.coinGeckoClient = coinGeckoClient;
        this.catalogPublisher = catalogPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Resultado de uma rodada, para o log e para os testes. */
    public record RoundResult(int assets, int updated, int candles, boolean aborted) {
    }

    // ------------------------------------------------------------------
    // B3
    // ------------------------------------------------------------------

    public synchronized RoundResult runB3Closing(String reason) {
        if (!brApiClient.hasToken()) {
            log.warn("Fechamento B3 ({}): BRAPI_TOKEN vazio — sem token a BRAPI so responde 4 tickers de teste. Rodada nao executada.", reason);
            return new RoundResult(0, 0, 0, true);
        }
        List<Asset> assets = openPositions.assetsWithShares(PriceSource.BRAPI);
        log.info("Fechamento B3 ({}): {} ativos em carteira", reason, assets.size());

        Set<Long> updated = new HashSet<>();
        int candles = 0;
        int networkFailures = 0;
        boolean aborted = false;
        LocalDate today = LocalDate.now(clock);

        for (Asset asset : assets) {
            String range = rangeFor(asset.getId(), today);
            try {
                BrApiHistory history = brApiClient.fetchDailyHistory(asset.getTicker(), range);
                networkFailures = 0;
                if (history.changed() && history.symbol() != null
                        && !history.symbol().equalsIgnoreCase(asset.getTicker())) {
                    log.warn("Ticker renomeado na B3: {} agora e {} — o catalogo NAO foi trocado; decidir manualmente.",
                            asset.getTicker(), history.symbol());
                }
                int saved = saveCandles(asset.getId(), history.candles());
                if (saved > 0) {
                    updated.add(asset.getId());
                    candles += saved;
                }
            } catch (BrApiException e) {
                if (e.isAuthOrPlan()) {
                    log.error("Fechamento B3 interrompido em {}: {} — confira o BRAPI_TOKEN e o plano.", asset.getTicker(), e.getMessage());
                    aborted = true;
                    break;
                }
                if (e.isRateLimited()) {
                    log.error("Fechamento B3 interrompido em {}: cota ou simultaneidade esgotada ({}).", asset.getTicker(), e.getMessage());
                    aborted = true;
                    break;
                }
                if (e.isNotFound()) {
                    log.warn("Fechamento B3: {} sem cotacao na BRAPI (404) — pulado.", asset.getTicker());
                    continue;
                }
                log.warn("Fechamento B3: {} falhou: {}", asset.getTicker(), e.getMessage());
                if (e.status() == 0 && ++networkFailures >= MAX_NETWORK_FAILURES) {
                    log.error("Fechamento B3 interrompido: {} falhas de rede seguidas.", networkFailures);
                    aborted = true;
                    break;
                }
            }
        }

        publishChange(updated);
        log.info("Fechamento B3 ({}) concluido: {}/{} ativos atualizados, {} pregoes gravados (carteira avisada), RateLimit-Remaining={}{}",
                reason, updated.size(), assets.size(), candles, brApiClient.lastRateLimitRemaining(),
                aborted ? " (INTERROMPIDO)" : "");
        return new RoundResult(assets.size(), updated.size(), candles, aborted);
    }

    /**
     * Quantos pregoes pedir. Sem historico: carga inicial de 3 meses. Com
     * historico: o menor range que cubra o que falta.
     */
    String rangeFor(Long assetId, LocalDate today) {
        LocalDate last = historyRepository.findTopByAssetIdOrderByReferenceDateDesc(assetId)
                .map(AssetPriceHistory::getReferenceDate)
                .orElse(null);
        if (last == null) {
            return "3mo";
        }
        long missing = businessDaysBetween(last, today);
        if (missing <= 3) {
            return "5d";
        }
        if (missing <= 20) {
            return "1mo";
        }
        return "3mo";
    }

    private int saveCandles(Long assetId, List<BrApiCandle> candles) {
        List<BrApiCandle> valid = candles.stream()
                .filter(c -> c.date() != null && c.close() != null && c.close().signum() > 0)
                .sorted(Comparator.comparing(BrApiCandle::date))
                .toList();
        if (valid.isEmpty()) {
            return 0;
        }
        return Objects.requireNonNull(tx.execute(status -> {
            Asset asset = assetRepository.findById(assetId).orElseThrow();
            int saved = 0;
            for (BrApiCandle c : valid) {
                LocalDate day = c.day();
                AssetPriceHistory row = historyRepository.findByAssetIdAndReferenceDate(assetId, day)
                        .orElseGet(() -> AssetPriceHistory.builder().asset(asset).referenceDate(day).build());
                if (row.getSource() == PriceHistorySource.B3_COTAHIST) {
                    continue; // a serie oficial da B3 vence (V29)
                }
                row.setSource(PriceHistorySource.BRAPI);
                row.setOpenPrice(c.open());
                row.setHighPrice(c.high());
                row.setLowPrice(c.low());
                row.setClosePrice(c.close());
                row.setVolume(c.volume());
                historyRepository.save(row);
                saved++;
            }
            BrApiCandle latest = valid.get(valid.size() - 1);
            asset.setCurrentPrice(latest.close());
            asset.setPriceUpdatedAt(LocalDateTime.now(clock));
            assetRepository.save(asset);
            return saved;
        }));
    }

    // ------------------------------------------------------------------
    // Cripto
    // ------------------------------------------------------------------

    public synchronized RoundResult runCryptoClosing(String reason) {
        List<Asset> assets = openPositions.assetsWithShares(PriceSource.COINGECKO);
        if (assets.isEmpty()) {
            log.info("Fechamento cripto ({}): nenhuma cripto em carteira", reason);
            return new RoundResult(0, 0, 0, false);
        }
        List<String> ids = assets.stream().map(Asset::getPriceSourceRef).filter(Objects::nonNull).toList();
        assets.stream().filter(a -> a.getPriceSourceRef() == null).forEach(a ->
                log.warn("Fechamento cripto: {} sem id da CoinGecko (price_source_ref) — pulado.", a.getTicker()));

        Map<String, BigDecimal> prices;
        try {
            prices = coinGeckoClient.fetchPricesInBrl(ids);
        } catch (RuntimeException e) {
            log.error("Fechamento cripto ({}) falhou: {}", reason, e.getMessage());
            return new RoundResult(assets.size(), 0, 0, true);
        }

        LocalDate today = LocalDate.now(clock);
        Set<Long> updated = new HashSet<>();
        for (Asset asset : assets) {
            BigDecimal price = asset.getPriceSourceRef() != null ? prices.get(asset.getPriceSourceRef()) : null;
            if (price == null || price.signum() <= 0) {
                if (asset.getPriceSourceRef() != null) {
                    log.warn("Fechamento cripto: {} ({}) sem preco na resposta.", asset.getTicker(), asset.getPriceSourceRef());
                }
                continue;
            }
            tx.executeWithoutResult(status -> {
                Asset a = assetRepository.findById(asset.getId()).orElseThrow();
                AssetPriceHistory row = historyRepository.findByAssetIdAndReferenceDate(a.getId(), today)
                        .orElseGet(() -> AssetPriceHistory.builder().asset(a).referenceDate(today).build());
                row.setSource(PriceHistorySource.COINGECKO);
                row.setClosePrice(price);
                historyRepository.save(row);
                a.setCurrentPrice(price);
                a.setPriceUpdatedAt(LocalDateTime.now(clock));
                assetRepository.save(a);
            });
            updated.add(asset.getId());
        }
        publishChange(updated);
        log.info("Fechamento cripto ({}) concluido: {}/{} ativos atualizados (carteira avisada)",
                reason, updated.size(), assets.size());
        return new RoundResult(assets.size(), updated.size(), updated.size(), false);
    }

    /** "Catalogo mudou" (FECHAMENTO): a app-api revaloriza as posicoes desses ativos. */
    private void publishChange(Set<Long> assetIds) {
        if (!assetIds.isEmpty()) {
            catalogPublisher.publish(new CatalogChange(CatalogChange.Origin.FECHAMENTO, assetIds));
        }
    }

    // ------------------------------------------------------------------
    // Recuperacao (a API roda na maquina do usuario e pode estar desligada as 19h)
    // ------------------------------------------------------------------

    /** Algum ativo B3 em carteira ficou sem o ultimo fechamento que ja deveria existir. */
    public boolean b3NeedsRecovery() {
        LocalDateTime expected = lastExpectedClosing(true);
        return openPositions.assetsWithShares(PriceSource.BRAPI).stream()
                .anyMatch(a -> a.getPriceUpdatedAt() == null || a.getPriceUpdatedAt().isBefore(expected));
    }

    /** Alguma cripto em carteira ficou sem o fechamento do dia (ou de ontem, antes das 19h). */
    public boolean cryptoNeedsRecovery() {
        LocalDateTime expected = lastExpectedClosing(false);
        return openPositions.assetsWithShares(PriceSource.COINGECKO).stream()
                .anyMatch(a -> a.getPriceUpdatedAt() == null || a.getPriceUpdatedAt().isBefore(expected));
    }

    /**
     * O ultimo horario de fechamento que ja passou. Na B3 so dias uteis
     * (feriado nao e conhecido aqui: a rodada do feriado so regrava o pregao
     * anterior); na cripto, todo dia.
     */
    LocalDateTime lastExpectedClosing(boolean businessDaysOnly) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate day = now.toLocalTime().isBefore(CLOSING_TIME) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        if (businessDaysOnly) {
            while (isWeekend(day)) {
                day = day.minusDays(1);
            }
        }
        return day.atTime(CLOSING_TIME);
    }

    static long businessDaysBetween(LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        long days = 0;
        for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            if (!isWeekend(d)) {
                days++;
            }
        }
        return Math.min(days, ChronoUnit.DAYS.between(from, to));
    }

    private static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
