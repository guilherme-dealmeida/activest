package com.primevest.scheduler;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.portfolio.AppreciationUpdater;
import com.primevest.domain.pricehistory.AssetPriceHistory;
import com.primevest.domain.pricehistory.AssetPriceHistoryRepository;
import com.primevest.integration.brapi.BrApiClient;
import com.primevest.integration.brapi.BrApiQuoteResponse;
import com.primevest.integration.coingecko.CoinGeckoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scheduler para atualização automática de preços de ativos.
 *
 * <p>Regras de atualização:</p>
 * <ul>
 *   <li><b>Pregão B3 (10h-18h, dias úteis):</b> A cada 15 minutos via BrAPI</li>
 *   <li><b>Fora do pregão:</b> 1x ao dia (fechamento)</li>
 *   <li><b>Crypto:</b> A cada 30 minutos via CoinGecko (24/7)</li>
 * </ul>
 *
 * <p>Toda cotacao alterada termina em portfolio_assets.appreciation: as
 * posicoes dos ativos atualizados tem a valorizacao regravada no fim de cada
 * rodada, pelo {@link AppreciationUpdater}. A tela le o valor gravado.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceUpdateScheduler {

    private final AssetRepository assetRepository;
    private final AssetPriceHistoryRepository priceHistoryRepository;
    private final BrApiClient brApiClient;
    private final CoinGeckoClient coinGeckoClient;
    private final AppreciationUpdater appreciationUpdater;

    private static final int BATCH_SIZE = 15;

    /**
     * Atualiza preços de ativos da B3 durante o horário de pregão.
     * Executa a cada 15 minutos, de segunda a sexta, entre 10h e 18h (horário de São Paulo).
     */
    @Scheduled(cron = "0 */15 10-18 * * MON-FRI", zone = "America/Sao_Paulo")
    @Transactional
    public void updateB3PricesDuringMarketHours() {
        log.info("Starting B3 price update (market hours)...");
        updateB3Prices();
    }

    /**
     * Atualiza preços de ativos da B3 no fechamento do pregão.
     * Executa às 18:30 de segunda a sexta.
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "America/Sao_Paulo")
    @Transactional
    public void updateB3PricesAtClose() {
        log.info("Starting B3 price update (market close)...");
        updateB3Prices();
        saveDailyPriceHistory();
    }

    /**
     * Atualiza preços de criptomoedas.
     * Executa a cada 30 minutos, 24/7.
     */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void updateCryptoPrices() {
        log.info("Starting crypto price update...");

        List<Asset> cryptoAssets = assetRepository.findByActiveTrue()
                .stream()
                .filter(a -> a.getAssetType() != null && a.getAssetType().getPriceSource() == PriceSource.COINGECKO)
                .toList();

        if (cryptoAssets.isEmpty()) {
            log.debug("No active crypto assets to update");
            return;
        }

        List<String> tickers = cryptoAssets.stream()
                .map(Asset::getTicker)
                .toList();

        Map<String, BigDecimal> prices = coinGeckoClient.getBatchPricesByTicker(tickers);

        Set<Long> updatedIds = new HashSet<>();
        for (Asset asset : cryptoAssets) {
            BigDecimal newPrice = prices.get(asset.getTicker());
            if (newPrice != null) {
                asset.setCurrentPrice(newPrice);
                asset.setPriceUpdatedAt(LocalDateTime.now());
                assetRepository.save(asset);
                updatedIds.add(asset.getId());
            }
        }

        log.info("Crypto price update complete. Updated {}/{} assets", updatedIds.size(), cryptoAssets.size());
        int positions = appreciationUpdater.refreshAssets(updatedIds);
        log.info("Crypto appreciation saved for {} positions", positions);
    }

    /**
     * Atualiza preços de ativos da B3 (Ações, FIIs, ETFs, BDRs, etc.).
     */
    private void updateB3Prices() {
        List<Asset> b3Assets = assetRepository.findByActiveTrue()
                .stream()
                .filter(a -> a.getAssetType() != null && a.getAssetType().getPriceSource() == PriceSource.BRAPI)
                .filter(a -> a.getTicker() != null && !a.getTicker().isBlank())
                .toList();

        if (b3Assets.isEmpty()) {
            log.debug("No active B3 assets to update");
            return;
        }

        // Processar em batches de BATCH_SIZE
        List<List<Asset>> batches = partition(b3Assets, BATCH_SIZE);
        int totalUpdated = 0;
        Set<Long> updatedIds = new HashSet<>();

        for (List<Asset> batch : batches) {
            List<String> tickers = batch.stream()
                    .map(Asset::getTicker)
                    .toList();

            List<BrApiQuoteResponse.BrApiQuote> quotes = brApiClient.getBatchQuotes(tickers);

            for (BrApiQuoteResponse.BrApiQuote quote : quotes) {
                b3Assets.stream()
                        .filter(a -> a.getTicker().equalsIgnoreCase(quote.getSymbol()))
                        .findFirst()
                        .ifPresent(asset -> {
                            if (quote.getRegularMarketPrice() != null) {
                                asset.setCurrentPrice(quote.getRegularMarketPrice());
                                asset.setPriceUpdatedAt(LocalDateTime.now());
                                assetRepository.save(asset);
                                updatedIds.add(asset.getId());
                            }
                        });
                totalUpdated++;
            }

            // Pequena pausa entre batches para não exceder rate limit da BrAPI
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("B3 price update complete. Updated {}/{} assets", totalUpdated, b3Assets.size());
        int positions = appreciationUpdater.refreshAssets(updatedIds);
        log.info("B3 appreciation saved for {} positions", positions);
    }

    /**
     * Salva o preço de fechamento do dia no histórico.
     */
    private void saveDailyPriceHistory() {
        LocalDate today = LocalDate.now();

        List<Asset> assets = assetRepository.findByActiveTrue()
                .stream()
                .filter(a -> a.getCurrentPrice() != null && a.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        int saved = 0;
        for (Asset asset : assets) {
            // Verificar se já existe registro para hoje
            if (priceHistoryRepository.findByAssetIdAndReferenceDate(asset.getId(), today).isEmpty()) {
                AssetPriceHistory history = AssetPriceHistory.builder()
                        .asset(asset)
                        .referenceDate(today)
                        .closePrice(asset.getCurrentPrice())
                        .build();
                priceHistoryRepository.save(history);
                saved++;
            }
        }

        log.info("Daily price history saved for {} assets", saved);
    }

    /**
     * Particiona uma lista em sublistas de tamanho máximo.
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
