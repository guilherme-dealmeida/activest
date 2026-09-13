package com.primevest.domain.portfolio;

import com.primevest.domain.portfolio.dto.*;
import com.primevest.domain.portfolio.dto.PortfolioPerformanceDTO.AssetPerformance;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final UserRepository userRepository;

    public PortfolioService(PortfolioRepository portfolioRepository, PortfolioAssetRepository portfolioAssetRepository, UserRepository userRepository) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.userRepository = userRepository;
    }

    /**
     * Gets portfolio summary — calculates totals from portfolio_assets instead of the portfolio table.
     */
    public PortfolioSummaryDTO getPortfolioSummary(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal totalDividends = BigDecimal.ZERO;

        for (PortfolioAsset pa : assets) {
            totalInvested = totalInvested.add(safe(pa.getTotalInvested()));
            totalDividends = totalDividends.add(safe(pa.getTotalDividends()));
            
            // currentValue: use asset's current price * shares if available, otherwise use stored currentValue
            BigDecimal assetCurrentPrice = pa.getAsset() != null ? pa.getAsset().getCurrentPrice() : null;
            if (assetCurrentPrice != null && assetCurrentPrice.compareTo(BigDecimal.ZERO) > 0) {
                currentValue = currentValue.add(assetCurrentPrice.multiply(safe(pa.getTotalShares())));
            } else {
                // For fixed income and others without live price, use the stored current_value
                currentValue = currentValue.add(safe(pa.getCurrentValue()));
            }
        }

        BigDecimal totalReturn = currentValue.add(totalDividends).subtract(totalInvested);
        BigDecimal returnPercentage = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            returnPercentage = totalReturn.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        return new PortfolioSummaryDTO(
            portfolio.getId(), portfolio.getName(),
            totalInvested, currentValue, totalDividends,
            totalReturn, returnPercentage
        );
    }

    /**
     * Gets portfolio assets with calculated values.
     */
    public List<PortfolioAssetDetailDTO> getPortfolioAssets(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        return portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId()).stream()
            .filter(pa -> safe(pa.getTotalInvested()).compareTo(BigDecimal.ZERO) > 0) // only show assets with investment
            .map(pa -> {
                BigDecimal assetPrice = pa.getAsset() != null ? pa.getAsset().getCurrentPrice() : null;
                BigDecimal currentValue;
                BigDecimal currentPrice;

                if (assetPrice != null && assetPrice.compareTo(BigDecimal.ZERO) > 0) {
                    currentPrice = assetPrice;
                    currentValue = currentPrice.multiply(safe(pa.getTotalShares()));
                } else {
                    // Fixed income — use stored values
                    currentValue = safe(pa.getCurrentValue());
                    currentPrice = safe(pa.getTotalShares()).compareTo(BigDecimal.ZERO) > 0
                        ? currentValue.divide(safe(pa.getTotalShares()), 4, RoundingMode.HALF_UP)
                        : safe(pa.getAveragePrice());
                }

                BigDecimal invested = safe(pa.getTotalInvested());
                BigDecimal valorization = currentValue.subtract(invested);
                BigDecimal returnPct = BigDecimal.ZERO;
                if (invested.compareTo(BigDecimal.ZERO) > 0) {
                    returnPct = valorization.divide(invested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }

                return new PortfolioAssetDetailDTO(
                    pa.getId(),
                    pa.getAsset().getTicker(),
                    pa.getAsset().getName(),
                    pa.getAsset().getAssetType().getDescription() != null ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName(),
                    pa.getAsset().getAssetCategory() != null ? pa.getAsset().getAssetCategory().getName() : null,
                    safe(pa.getTotalShares()),
                    safe(pa.getAveragePrice()),
                    invested,
                    currentValue,
                    currentPrice,
                    safe(pa.getTotalDividends()),
                    valorization,
                    returnPct,
                    pa.isReservation()
                );
            }).collect(Collectors.toList());
    }

    /**
     * Gets portfolio allocation by asset type.
     */
    public List<PortfolioAllocationDTO> getAllocation(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());

        // Calculate total current value from assets
        BigDecimal totalVal = BigDecimal.ZERO;
        Map<String, BigDecimal> typeValues = new HashMap<>();
        Map<String, String> typeColors = new HashMap<>();

        for (PortfolioAsset pa : assets) {
            if (safe(pa.getTotalInvested()).compareTo(BigDecimal.ZERO) == 0) continue;
            
            String typeName = pa.getAsset().getAssetType().getDescription() != null
                ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName();
            typeColors.put(typeName, pa.getAsset().getAssetType().getColorHex());
            
            BigDecimal assetPrice = pa.getAsset().getCurrentPrice();
            BigDecimal val;
            if (assetPrice != null && assetPrice.compareTo(BigDecimal.ZERO) > 0) {
                val = assetPrice.multiply(safe(pa.getTotalShares()));
            } else {
                val = safe(pa.getCurrentValue());
            }
            totalVal = totalVal.add(val);
            typeValues.merge(typeName, val, BigDecimal::add);
        }

        if (totalVal.compareTo(BigDecimal.ZERO) == 0) return List.of();

        final BigDecimal total = totalVal;
        return typeValues.entrySet().stream().map(e -> {
            BigDecimal pct = e.getValue().divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            return new PortfolioAllocationDTO(e.getKey(), typeColors.get(e.getKey()), e.getValue(), pct);
        }).collect(Collectors.toList());
    }

    /**
     * Gets detailed allocation — grouped by asset type, then by category within each type.
     */
    public List<PortfolioAllocationDetailedDTO> getAllocationDetailed(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());

        // Calculate total portfolio value
        BigDecimal totalPortfolioValue = BigDecimal.ZERO;

        // Group: typeName -> { colorHex, categoryName -> value }
        Map<String, String> typeColorMap = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> typeCategories = new LinkedHashMap<>();

        for (PortfolioAsset pa : assets) {
            if (safe(pa.getTotalInvested()).compareTo(BigDecimal.ZERO) == 0) continue;

            String typeName = pa.getAsset().getAssetType().getDescription() != null
                ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName();
            String colorHex = pa.getAsset().getAssetType().getColorHex();
            String categoryName = pa.getAsset().getAssetCategory() != null
                ? pa.getAsset().getAssetCategory().getName()
                : "Sem Categoria";

            BigDecimal assetPrice = pa.getAsset().getCurrentPrice();
            BigDecimal val;
            if (assetPrice != null && assetPrice.compareTo(BigDecimal.ZERO) > 0) {
                val = assetPrice.multiply(safe(pa.getTotalShares()));
            } else {
                val = safe(pa.getCurrentValue());
            }

            totalPortfolioValue = totalPortfolioValue.add(val);
            typeColorMap.put(typeName, colorHex);
            typeCategories.computeIfAbsent(typeName, k -> new LinkedHashMap<>())
                .merge(categoryName, val, BigDecimal::add);
        }

        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) == 0) return List.of();

        final BigDecimal totalVal = totalPortfolioValue;
        List<PortfolioAllocationDetailedDTO> result = new ArrayList<>();

        for (Map.Entry<String, Map<String, BigDecimal>> typeEntry : typeCategories.entrySet()) {
            String typeName = typeEntry.getKey();
            Map<String, BigDecimal> categories = typeEntry.getValue();

            BigDecimal typeTotal = categories.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal typePct = typeTotal.divide(totalVal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            List<PortfolioAllocationDetailedDTO.CategoryAllocation> catAllocations = categories.entrySet().stream()
                .map(catEntry -> {
                    BigDecimal catPct = typeTotal.compareTo(BigDecimal.ZERO) > 0
                        ? catEntry.getValue().divide(typeTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
                    return new PortfolioAllocationDetailedDTO.CategoryAllocation(
                        catEntry.getKey(), catEntry.getValue(), catPct
                    );
                })
                .collect(Collectors.toList());

            result.add(new PortfolioAllocationDetailedDTO(
                typeName, typeColorMap.get(typeName), typeTotal, typePct, catAllocations
            ));
        }

        return result;
    }

    /**
     * Gets performance.
     */
    public PortfolioPerformanceDTO getPerformance(UUID userId) {
        PortfolioSummaryDTO summary = getPortfolioSummary(userId);
        Portfolio portfolio = getOrCreatePortfolio(userId);

        List<AssetPerformance> assetPerfs = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId()).stream()
            .filter(pa -> safe(pa.getTotalInvested()).compareTo(BigDecimal.ZERO) > 0)
            .map(pa -> {
                BigDecimal invested = safe(pa.getTotalInvested());
                BigDecimal assetPrice = pa.getAsset() != null ? pa.getAsset().getCurrentPrice() : null;
                BigDecimal currentValue;
                if (assetPrice != null && assetPrice.compareTo(BigDecimal.ZERO) > 0) {
                    currentValue = assetPrice.multiply(safe(pa.getTotalShares()));
                } else {
                    currentValue = safe(pa.getCurrentValue());
                }
                BigDecimal returnPct = BigDecimal.ZERO;
                if (invested.compareTo(BigDecimal.ZERO) > 0) {
                    returnPct = currentValue.subtract(invested).divide(invested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }
                return new AssetPerformance(pa.getAsset().getTicker(), invested, currentValue, returnPct);
            }).collect(Collectors.toList());

        return new PortfolioPerformanceDTO(
            summary.totalInvested(), summary.currentValue(), summary.totalDividends(),
            summary.totalReturn(), summary.returnPercentage(), assetPerfs
        );
    }

    @Transactional
    public Portfolio getOrCreatePortfolio(UUID userId) {
        return portfolioRepository.findFirstByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElseThrow();
            Portfolio p = new Portfolio();
            p.setUser(user);
            p.setName("Main Portfolio");
            p.setTotalInvested(BigDecimal.ZERO);
            p.setCurrentValue(BigDecimal.ZERO);
            p.setTotalDividends(BigDecimal.ZERO);
            return portfolioRepository.save(p);
        });
    }

    @Transactional
    public void recalculatePortfolio(UUID portfolioId) {
        Portfolio p = portfolioRepository.findById(portfolioId).orElseThrow();
        List<PortfolioAsset> assets = portfolioAssetRepository.findByPortfolioId(portfolioId);
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal totalDividends = BigDecimal.ZERO;
        for (PortfolioAsset pa : assets) {
            totalInvested = totalInvested.add(safe(pa.getTotalInvested()));
            totalDividends = totalDividends.add(safe(pa.getTotalDividends()));
            BigDecimal price = pa.getAsset() != null ? pa.getAsset().getCurrentPrice() : null;
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                currentValue = currentValue.add(price.multiply(safe(pa.getTotalShares())));
            } else {
                currentValue = currentValue.add(safe(pa.getCurrentValue()));
            }
        }
        p.setTotalInvested(totalInvested);
        p.setCurrentValue(currentValue);
        p.setTotalDividends(totalDividends);
        portfolioRepository.save(p);
    }

    private BigDecimal safe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
