package com.primevest.domain.dividend;

import com.primevest.domain.dividend.dto.DividendCreateDTO;
import com.primevest.domain.dividend.dto.DividendResponseDTO;
import com.primevest.domain.dividend.dto.DividendSummaryDTO;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DividendService {

    private final DividendRepository dividendRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final AssetRepository assetRepository;

    /**
     * Gets dividend summary for the user.
     */
    public DividendSummaryDTO getDividendSummary(UUID userId) {
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return new DividendSummaryDTO(BigDecimal.ZERO, Map.of(), List.of());
        }

        // Get all portfolio assets for this portfolio
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolio.getId());
        
        // Collect all dividends
        List<Dividend> allDividends = new ArrayList<>();
        for (PortfolioAsset pa : portfolioAssets) {
            allDividends.addAll(dividendRepository.findByPortfolioAssetIdOrderByExDateDesc(pa.getId()));
        }

        // Calculate total
        BigDecimal total = allDividends.stream()
            .map(d -> d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group by type
        Map<String, BigDecimal> byType = new HashMap<>();
        byType.put("DIVIDENDO", BigDecimal.ZERO);
        byType.put("JCP", BigDecimal.ZERO);
        byType.put("RENDIMENTO", BigDecimal.ZERO);
        for (Dividend d : allDividends) {
            String typeName = d.getType().name();
            BigDecimal amount = d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO;
            byType.merge(typeName, amount, BigDecimal::add);
        }

        // Recent dividends (sorted by exDate desc)
        List<DividendResponseDTO> recent = allDividends.stream()
            .sorted(Comparator.comparing(Dividend::getExDate).reversed())
            .map(this::toResponseDTO)
            .collect(Collectors.toList());

        return new DividendSummaryDTO(total, byType, recent);
    }

    /**
     * Registers a dividend.
     */
    @Transactional
    public DividendResponseDTO registerDividend(UUID userId, DividendCreateDTO dto) {
        Asset asset = assetRepository.findByTicker(dto.ticker())
            .orElseThrow(() -> new RuntimeException("Asset not found: " + dto.ticker()));

        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Portfolio not found"));

        PortfolioAsset pa = portfolioAssetRepository.findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
            .orElse(null);

        Dividend dividend = Dividend.builder()
            .asset(asset)
            .portfolioAsset(pa)
            .amountPerShare(dto.amountPerShare())
            .totalAmount(dto.amountPerShare()) // default to amountPerShare if no total provided
            .exDate(dto.exDate())
            .paymentDate(dto.paymentDate())
            .type(dto.type())
            .build();

        dividend = dividendRepository.save(dividend);
        return toResponseDTO(dividend);
    }

    /**
     * Gets dividends by asset ticker.
     */
    public List<DividendResponseDTO> getDividendsByAsset(UUID userId, String ticker) {
        Asset asset = assetRepository.findByTicker(ticker).orElse(null);
        if (asset == null) return List.of();

        return dividendRepository.findByAssetIdOrderByExDateDesc(asset.getId())
            .stream()
            .map(this::toResponseDTO)
            .collect(Collectors.toList());
    }

    private DividendResponseDTO toResponseDTO(Dividend d) {
        return new DividendResponseDTO(
            d.getId(),
            d.getAsset() != null ? d.getAsset().getTicker() : "",
            d.getAsset() != null ? d.getAsset().getName() : "",
            d.getAmountPerShare(),
            d.getTotalAmount(),
            d.getExDate(),
            d.getPaymentDate(),
            d.getType()
        );
    }
}
