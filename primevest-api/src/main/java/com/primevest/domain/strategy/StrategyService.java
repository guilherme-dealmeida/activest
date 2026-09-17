package com.primevest.domain.strategy;

import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.AssetTypeRepository;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.portfolio.Position;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.strategy.dto.RebalanceDTO;
import com.primevest.domain.strategy.dto.StrategyDTO;
import com.primevest.domain.strategy.dto.StrategyUpdateDTO;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyRepository strategyRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final UserRepository userRepository;
    private final PositionCalculator positionCalculator;

    /**
     * Gets user strategy.
     */
    public List<StrategyDTO> getUserStrategy(UUID userId) {
        List<Strategy> strategies = strategyRepository.findByUserId(userId);
        return strategies.stream()
            .map(s -> new StrategyDTO(
                s.getId(),
                s.getAssetType().getId(),
                s.getAssetType().getDescription() != null ? s.getAssetType().getDescription() : s.getAssetType().getName(),
                s.getTargetPercentage(),
                s.isIncludeReservation()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Updates user strategy.
     */
    @Transactional
    public List<StrategyDTO> updateStrategy(UUID userId, StrategyUpdateDTO dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found"));
        
        // Delete all existing strategy items for this user
        List<Strategy> existing = strategyRepository.findByUserId(userId);
        strategyRepository.deleteAll(existing);
        strategyRepository.flush();
        
        // Create new items from the request
        for (StrategyUpdateDTO.StrategyItemDTO item : dto.items()) {
            AssetType assetType = assetTypeRepository.findById(item.assetTypeId())
                .orElseThrow(() -> new BusinessException("Asset type not found: " + item.assetTypeId()));
            
            Strategy strategy = Strategy.builder()
                .user(user)
                .assetType(assetType)
                .targetPercentage(item.targetPercentage())
                .includeReservation(item.includeReservation())
                .build();
            strategyRepository.save(strategy);
        }
        
        return getUserStrategy(userId);
    }

    /**
     * Deletes a strategy item for a specific asset type.
     */
    @Transactional
    public void deleteStrategyItem(UUID userId, Long assetTypeId) {
        Strategy strategy = strategyRepository.findByUserIdAndAssetTypeId(userId, assetTypeId)
            .orElseThrow(() -> new BusinessException("Strategy item not found"));
        strategyRepository.delete(strategy);
    }

    /**
     * Gets rebalance suggestion based on current portfolio vs target strategy.
     */
    public RebalanceDTO getRebalanceSuggestion(UUID userId) {
        List<Strategy> strategies = strategyRepository.findByUserId(userId);
        
        // Get all asset types for naming
        List<AssetType> allTypes = assetTypeRepository.findAll();
        Map<Long, String> typeNames = allTypes.stream()
            .collect(Collectors.toMap(AssetType::getId, AssetType::getName));

        // Get portfolio assets
        var portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        List<PortfolioAsset> portfolioAssets = portfolio != null 
            ? portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId())
            : List.of();

        // Custo de cada posicao, apurado das transacoes: nao e mais gravado.
        Map<UUID, Position> positions = portfolio != null
            ? positionCalculator.positionsOf(portfolio.getId(), portfolioAssets)
            : Map.of();

        // Calculate total portfolio value
        BigDecimal totalValue = portfolioAssets.stream()
            .map(pa -> invested(positions, pa))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group portfolio value by asset type
        Map<Long, BigDecimal> valueByType = new HashMap<>();
        for (PortfolioAsset pa : portfolioAssets) {
            if (pa.getAsset() != null && pa.getAsset().getAssetType() != null) {
                Long typeId = pa.getAsset().getAssetType().getId();
                BigDecimal value = invested(positions, pa);
                valueByType.merge(typeId, value, BigDecimal::add);
            }
        }

        // Build rebalance items
        List<RebalanceDTO.RebalanceItem> items = new ArrayList<>();
        
        // If no strategies defined, create items for all asset types with 0% target
        if (strategies.isEmpty()) {
            for (AssetType type : allTypes) {
                BigDecimal currentValue = valueByType.getOrDefault(type.getId(), BigDecimal.ZERO);
                BigDecimal currentPct = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? currentValue.multiply(BigDecimal.valueOf(100)).divide(totalValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                items.add(new RebalanceDTO.RebalanceItem(
                    type.getDescription() != null ? type.getDescription() : type.getName(),
                    BigDecimal.ZERO, currentPct, currentPct.negate(),
                    currentValue, BigDecimal.ZERO, currentValue.negate()
                ));
            }
        } else {
            for (Strategy strategy : strategies) {
                Long typeId = strategy.getAssetType().getId();
                String typeName = strategy.getAssetType().getDescription() != null 
                    ? strategy.getAssetType().getDescription() : strategy.getAssetType().getName();
                BigDecimal targetPct = strategy.getTargetPercentage();
                BigDecimal currentValue = valueByType.getOrDefault(typeId, BigDecimal.ZERO);
                
                BigDecimal currentPct = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? currentValue.multiply(BigDecimal.valueOf(100)).divide(totalValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                
                BigDecimal diffPct = currentPct.subtract(targetPct);
                BigDecimal idealValue = totalValue.multiply(targetPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal amountToRebalance = idealValue.subtract(currentValue);
                
                items.add(new RebalanceDTO.RebalanceItem(
                    typeName, targetPct, currentPct, diffPct,
                    currentValue, idealValue, amountToRebalance
                ));
            }
        }
        
        return new RebalanceDTO(totalValue, items);
    }

    private BigDecimal invested(Map<UUID, Position> positions, PortfolioAsset pa) {
        Position position = positions.get(pa.getId());
        return position != null && position.totalInvested() != null ? position.totalInvested() : BigDecimal.ZERO;
    }
}
