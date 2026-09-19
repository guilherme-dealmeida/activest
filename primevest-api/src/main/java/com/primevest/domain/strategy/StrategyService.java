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
     * Sugestao de aporte para levar a carteira aos ideais estrategicos.
     *
     * <p>Regra A: a base do calculo e o tipo que esta mais acima do seu ideal.
     * Dele sai quanto vale cada ponto percentual da carteira estrategica; os
     * demais tipos recebem aporte ate alcancar esse mesmo padrao. Assim o
     * rebalanceamento acontece so com aporte, sem precisar vender nada — e o
     * tipo que serviu de base fica com sugestao zero.</p>
     *
     * <p>Regra B: ativo cujo tipo nao tem ideal cadastrado nao entra na conta.
     * Regra C: posicao de reserva tambem fica de fora, a menos que o ideal do
     * tipo esteja marcado para incluir reserva.</p>
     */
    public RebalanceDTO getRebalanceSuggestion(UUID userId) {
        List<Strategy> strategies = strategyRepository.findByUserId(userId);
        List<AssetType> allTypes = assetTypeRepository.findAll();

        var portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        List<PortfolioAsset> portfolioAssets = portfolio != null
            ? portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId())
            : List.of();

        // Custo de cada posicao, apurado das transacoes: nao e mais gravado.
        Map<UUID, Position> positions = portfolio != null
            ? positionCalculator.positionsOf(portfolio.getId(), portfolioAssets)
            : Map.of();

        // Sem nenhum ideal cadastrado nao ha estrategia a comparar: a tela
        // mostra apenas como a carteira esta distribuida hoje.
        if (strategies.isEmpty()) {
            Map<Long, BigDecimal> valueByType = new HashMap<>();
            for (PortfolioAsset pa : portfolioAssets) {
                if (pa.getAsset() != null && pa.getAsset().getAssetType() != null) {
                    valueByType.merge(pa.getAsset().getAssetType().getId(), invested(positions, pa), BigDecimal::add);
                }
            }
            BigDecimal totalValue = valueByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            List<RebalanceDTO.RebalanceItem> items = new ArrayList<>();
            for (AssetType type : allTypes) {
                BigDecimal currentValue = valueByType.getOrDefault(type.getId(), BigDecimal.ZERO);
                BigDecimal currentPct = percentageOf(currentValue, totalValue);
                items.add(new RebalanceDTO.RebalanceItem(
                    type.getDescription() != null ? type.getDescription() : type.getName(),
                    BigDecimal.ZERO, currentPct, currentPct.negate(),
                    currentValue, BigDecimal.ZERO, currentValue.negate()
                ));
            }
            return new RebalanceDTO(totalValue, items);
        }

        Map<Long, Strategy> strategyByType = new LinkedHashMap<>();
        for (Strategy strategy : strategies) {
            strategyByType.put(strategy.getAssetType().getId(), strategy);
        }

        // Regras B e C: o que nao tem ideal para o seu tipo, e a reserva de
        // tipo que nao pediu para incluir reserva, ficam fora do total.
        Map<Long, BigDecimal> valueByType = new HashMap<>();
        for (PortfolioAsset pa : portfolioAssets) {
            if (pa.getAsset() == null || pa.getAsset().getAssetType() == null) {
                continue;
            }
            Long typeId = pa.getAsset().getAssetType().getId();
            Strategy strategy = strategyByType.get(typeId);
            if (strategy == null) {
                continue;
            }
            if (pa.isReservation() && !strategy.isIncludeReservation()) {
                continue;
            }
            valueByType.merge(typeId, invested(positions, pa), BigDecimal::add);
        }

        BigDecimal totalValue = valueByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Regra A: quanto vale um ponto percentual da carteira estrategica.
        // Sai do tipo com a maior razao aportado/ideal — o mais acima do seu
        // ideal. Empate (mais de um tipo na mesma razao) cai no mesmo valor e
        // nao muda nada; carteira ja equilibrada da a mesma razao para todos e
        // zera todas as sugestoes. Tipo com ideal zero nao serve de base: ele
        // nao tem espaco na estrategia, entao nao ha razao a comparar.
        BigDecimal valuePerPoint = BigDecimal.ZERO;
        for (Strategy strategy : strategies) {
            BigDecimal targetPct = targetOf(strategy);
            if (targetPct.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal currentValue = valueByType.getOrDefault(strategy.getAssetType().getId(), BigDecimal.ZERO);
            BigDecimal ratio = currentValue.divide(targetPct, 10, RoundingMode.HALF_UP);
            if (ratio.compareTo(valuePerPoint) > 0) {
                valuePerPoint = ratio;
            }
        }

        List<RebalanceDTO.RebalanceItem> items = new ArrayList<>();
        for (Strategy strategy : strategies) {
            Long typeId = strategy.getAssetType().getId();
            String typeName = strategy.getAssetType().getDescription() != null
                ? strategy.getAssetType().getDescription() : strategy.getAssetType().getName();
            BigDecimal targetPct = targetOf(strategy);
            BigDecimal currentValue = valueByType.getOrDefault(typeId, BigDecimal.ZERO);

            BigDecimal currentPct = percentageOf(currentValue, totalValue);
            BigDecimal diffPct = currentPct.subtract(targetPct);

            // Valor que o tipo passa a ter depois do aporte sugerido.
            BigDecimal idealValue = valuePerPoint.multiply(targetPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amountToRebalance = idealValue.subtract(currentValue);

            // So arredondamento pode deixar a base levemente negativa; o unico
            // negativo de verdade e o do tipo com ideal zero e dinheiro dentro.
            if (amountToRebalance.compareTo(BigDecimal.ZERO) < 0
                && amountToRebalance.compareTo(new BigDecimal("-0.01")) >= 0) {
                amountToRebalance = BigDecimal.ZERO;
            }

            items.add(new RebalanceDTO.RebalanceItem(
                typeName, targetPct, currentPct, diffPct,
                currentValue, idealValue, amountToRebalance
            ));
        }

        return new RebalanceDTO(totalValue, items);
    }

    private BigDecimal targetOf(Strategy strategy) {
        return strategy.getTargetPercentage() != null ? strategy.getTargetPercentage() : BigDecimal.ZERO;
    }

    private BigDecimal percentageOf(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    /**
     * O que a posicao ainda tem na carteira.
     *
     * <p>Posicao liquidada nao entra: ela virou dinheiro e nao ocupa mais
     * espaco na alocacao. Na Renda Fixa o valor aplicado nao desconta os
     * resgates — as regras E e F precisam das duas pontas separadas —, entao o
     * desconto e feito aqui.</p>
     */
    private BigDecimal invested(Map<UUID, Position> positions, PortfolioAsset pa) {
        Position position = positions.get(pa.getId());
        if (position == null || pa.isSettled()) {
            return BigDecimal.ZERO;
        }
        return position.invested().subtract(position.sold()).max(BigDecimal.ZERO);
    }
}
