package com.primevest.scheduler;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.dividend.Dividend;
import com.primevest.domain.dividend.DividendRepository;
import com.primevest.domain.dividend.DividendType;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.integration.brapi.BrApiClient;
import com.primevest.integration.brapi.BrApiDividendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduler para sincronização automática de dividendos/proventos.
 *
 * <p>Sincroniza dividendos de ações e FIIs cadastrados na carteira dos usuários,
 * buscando informações da BrAPI e associando aos PortfolioAssets correspondentes.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendSyncScheduler {

    private final AssetRepository assetRepository;
    private final DividendRepository dividendRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final BrApiClient brApiClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Sincroniza dividendos uma vez ao dia, às 20h.
     * Busca novos dividendos dos últimos 30 dias para todos os ativos
     * que possuem posições em carteiras de usuários.
     */
    @Scheduled(cron = "0 0 20 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void syncDividends() {
        log.info("Starting dividend sync...");

        // Buscar todos os tickers que estão em carteiras de usuários
        List<PortfolioAsset> allPortfolioAssets = portfolioAssetRepository.findAll();

        List<String> uniqueTickers = allPortfolioAssets.stream()
                .map(pa -> pa.getAsset().getTicker())
                .distinct()
                .filter(ticker -> ticker != null && !ticker.isBlank())
                .toList();

        int newDividends = 0;

        for (String ticker : uniqueTickers) {
            try {
                newDividends += syncDividendsForTicker(ticker, allPortfolioAssets);
                // Pausa para não exceder rate limit
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error syncing dividends for {}: {}", ticker, e.getMessage());
            }
        }

        log.info("Dividend sync complete. {} new dividends registered", newDividends);
    }

    /**
     * Sincroniza dividendos para um ticker específico.
     *
     * @param ticker          ticker do ativo
     * @param portfolioAssets lista de todos os portfolio assets
     * @return número de novos dividendos registrados
     */
    private int syncDividendsForTicker(String ticker, List<PortfolioAsset> portfolioAssets) {
        var brApiResponse = brApiClient.getDividends(ticker);

        if (brApiResponse.isEmpty() || brApiResponse.get().getDividends() == null) {
            return 0;
        }

        Asset asset = assetRepository.findByTicker(ticker).orElse(null);
        if (asset == null) {
            return 0;
        }

        int count = 0;
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);

        for (BrApiDividendResponse.BrApiDividend brApiDividend : brApiResponse.get().getDividends()) {
            try {
                LocalDate exDate = parseDate(brApiDividend.getLastDatePrior());
                if (exDate == null || exDate.isBefore(thirtyDaysAgo)) {
                    continue;
                }

                LocalDate paymentDate = parseDate(brApiDividend.getPaymentDate());
                BigDecimal amountPerShare = brApiDividend.getRate();
                DividendType type = parseDividendType(brApiDividend.getType());

                // Para cada portfolio_asset que contém este ativo,
                // verificar se o dividendo já foi registrado
                List<PortfolioAsset> relevantAssets = portfolioAssets.stream()
                        .filter(pa -> pa.getAsset().getId().equals(asset.getId()))
                        .filter(pa -> pa.getTotalShares().compareTo(BigDecimal.ZERO) > 0)
                        .toList();

                for (PortfolioAsset pa : relevantAssets) {
                    // Verificar se já existe este dividendo para este portfolio_asset
                    boolean alreadyExists = dividendRepository
                            .findByPortfolioAssetIdOrderByExDateDesc(pa.getId())
                            .stream()
                            .anyMatch(d -> d.getExDate().equals(exDate)
                                    && d.getAmountPerShare().compareTo(amountPerShare) == 0);

                    if (!alreadyExists) {
                        BigDecimal totalAmount = amountPerShare
                                .multiply(pa.getTotalShares())
                                .setScale(2, RoundingMode.HALF_UP);

                        Dividend dividend = Dividend.builder()
                                .asset(asset)
                                .portfolioAsset(pa)
                                .amountPerShare(amountPerShare)
                                .totalAmount(totalAmount)
                                .exDate(exDate)
                                .paymentDate(paymentDate)
                                .type(type)
                                .build();

                        dividendRepository.save(dividend);

                        // Atualizar totais de dividendos no portfolio_asset
                        pa.setTotalDividends(
                                pa.getTotalDividends().add(totalAmount)
                        );
                        portfolioAssetRepository.save(pa);

                        count++;
                        log.debug("New dividend registered: {} - {} per share on {}",
                                ticker, amountPerShare, exDate);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing dividend for {}: {}", ticker, e.getMessage());
            }
        }

        return count;
    }

    /**
     * Converte tipo de dividendo da BrAPI para o enum interno.
     */
    private DividendType parseDividendType(String type) {
        if (type == null) return DividendType.DIVIDENDO;
        return switch (type.toUpperCase()) {
            case "JCP", "JSCP", "JUROS SOBRE CAPITAL PROPRIO" -> DividendType.JCP;
            case "RENDIMENTO", "REND" -> DividendType.RENDIMENTO;
            default -> DividendType.DIVIDENDO;
        };
    }

    /**
     * Faz parse seguro de data no formato yyyy-MM-dd.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            // BrAPI pode retornar timestamps ou datas simples
            if (dateStr.contains("T")) {
                dateStr = dateStr.substring(0, 10);
            }
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Could not parse date: {}", dateStr);
            return null;
        }
    }
}
