package com.primevest.domain.dividend;

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
        byType.put("AMORTIZACAO", BigDecimal.ZERO);
        byType.put("RESTITUICAO_CAPITAL", BigDecimal.ZERO);
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
     * Gets dividends by asset ticker.
     */
    public List<DividendResponseDTO> getDividendsByAsset(UUID userId, String ticker) {
        Asset asset = assetRepository.findByTicker(ticker).orElse(null);
        if (asset == null) return List.of();

        return dividendRepository.findByAssetIdAndOwner(userId, asset.getId())
            .stream()
            .map(this::toResponseDTO)
            .collect(Collectors.toList());
    }

    private DividendResponseDTO toResponseDTO(Dividend d) {
        Asset asset = d.getPortfolioAsset() != null ? d.getPortfolioAsset().getAsset() : null;
        return new DividendResponseDTO(
            d.getId(),
            asset != null ? asset.getTicker() : "",
            asset != null ? asset.getName() : "",
            d.getAmountPerShare(),
            d.getTotalAmount(),
            d.getExDate(),
            d.getPaymentDate(),
            d.getType()
        );
    }
}
