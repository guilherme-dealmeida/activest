package com.primevest.domain.strategy;

import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.AssetTypeRepository;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.strategy.dto.RebalanceDTO;
import com.primevest.domain.strategy.dto.StrategyDTO;
import com.primevest.domain.strategy.dto.StrategyUpdateDTO;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
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

    private static final BigDecimal CEM = new BigDecimal("100.0");

    private final StrategyRepository strategyRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final UserRepository userRepository;
    private final StrategyHoldings strategyHoldings;
    private final StrategyReservationService strategyReservationService;

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
                s.getTargetPercentage()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Substitui a estrategia inteira do usuario (full replace).
     *
     * <p>Tres regras antes de gravar, todas conferidas sobre a requisicao e nao
     * sobre o que esta no banco:</p>
     * <ol>
     *   <li>tipo de ativo tem que existir — id inexistente responde "nao
     *   encontrado", como qualquer recurso;</li>
     *   <li>nenhum tipo repetido — antes isso batia na unicidade do banco e
     *   virava erro 500, que nao diz nada a quem chamou;</li>
     *   <li>a soma dos ideais e 100%.</li>
     * </ol>
     */
    @Transactional
    public List<StrategyDTO> updateStrategy(UUID userId, StrategyUpdateDTO dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        List<StrategyUpdateDTO.StrategyItemDTO> items =
            dto.items() != null ? dto.items() : List.of();

        Map<Long, AssetType> tipos = new LinkedHashMap<>();
        for (StrategyUpdateDTO.StrategyItemDTO item : items) {
            AssetType assetType = assetTypeRepository.findById(item.assetTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Tipo de ativo não encontrado: " + item.assetTypeId()));
            if (tipos.putIfAbsent(item.assetTypeId(), assetType) != null) {
                throw new BusinessException("O tipo " + displayNameOf(assetType)
                    + " aparece mais de uma vez na estratégia.");
            }
        }

        validateTotal(items);

        // Full replace: o que nao veio na requisicao deixa de existir.
        List<Strategy> existing = strategyRepository.findByUserId(userId);
        strategyRepository.deleteAll(existing);
        strategyRepository.flush();

        for (StrategyUpdateDTO.StrategyItemDTO item : items) {
            strategyRepository.save(Strategy.builder()
                .user(user)
                .assetType(tipos.get(item.assetTypeId()))
                .targetPercentage(item.targetPercentage())
                .build());
        }

        return getUserStrategy(userId);
    }

    /**
     * A soma dos ideais e 100%, com a tolerancia do arredondamento.
     *
     * <p>Cada item tem duas casas ({@code numeric(5,2)}), e dividir 100 em
     * partes iguais nunca fecha exato: 33,33 tres vezes da 99,99 e 8,33 doze
     * vezes da 99,96. Por isso a conferencia e sobre a soma arredondada para
     * UMA casa — a mesma conta que a tela ja faz antes de salvar.</p>
     *
     * <p>Lista vazia nao passa: zerar a estrategia por um PUT sem itens
     * apagaria tudo em silencio, e quem quer remover um tipo manda a lista sem
     * ele, redistribuindo os 100%.</p>
     */
    private void validateTotal(List<StrategyUpdateDTO.StrategyItemDTO> items) {
        BigDecimal soma = items.stream()
            .map(StrategyUpdateDTO.StrategyItemDTO::targetPercentage)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (soma.setScale(1, RoundingMode.HALF_UP).compareTo(CEM) != 0) {
            throw new BusinessException("A soma dos percentuais ideais deve ser 100%. Atual: "
                + soma.stripTrailingZeros().toPlainString() + "%");
        }
    }

    private String displayNameOf(AssetType type) {
        return type.getDescription() != null ? type.getDescription() : type.getName();
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
     * Regra C: posicao de reserva de emergencia nunca entra. A reserva
     * concorre com o resto da carteira como um bloco proprio, com a sua linha
     * em {@code reservation}; somada tambem aqui, o mesmo CDB seria contado
     * duas vezes. Os itens por tipo sao, portanto, a geracao de renda.</p>
     */
    public RebalanceDTO getRebalanceSuggestion(UUID userId) {
        List<Strategy> strategies = strategyRepository.findByUserId(userId);
        List<AssetType> allTypes = assetTypeRepository.findAll();

        // Custo de cada posicao, apurado das transacoes: nao e mais gravado.
        StrategyHoldings.Holdings holdings = strategyHoldings.of(userId);
        List<PortfolioAsset> portfolioAssets = holdings.assets();
        var reservation = strategyReservationService.rebalance(userId, holdings);

        // Sem nenhum ideal cadastrado nao ha estrategia a comparar: a tela
        // mostra apenas como a carteira esta distribuida hoje.
        if (strategies.isEmpty()) {
            Map<Long, BigDecimal> valueByType = new HashMap<>();
            for (PortfolioAsset pa : portfolioAssets) {
                // Regra C vale aqui tambem: geracao de renda tem uma definicao so.
                if (pa.isReservation()) {
                    continue;
                }
                if (pa.getAsset() != null && pa.getAsset().getAssetType() != null) {
                    valueByType.merge(pa.getAsset().getAssetType().getId(), holdings.held(pa), BigDecimal::add);
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
            return new RebalanceDTO(totalValue, items, reservation);
        }

        Map<Long, Strategy> strategyByType = new LinkedHashMap<>();
        for (Strategy strategy : strategies) {
            strategyByType.put(strategy.getAssetType().getId(), strategy);
        }

        // Regras B e C: o que nao tem ideal para o seu tipo, e toda posicao de
        // reserva, ficam fora do total.
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
            if (pa.isReservation()) {
                continue;
            }
            valueByType.merge(typeId, holdings.held(pa), BigDecimal::add);
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

        return new RebalanceDTO(totalValue, items, reservation);
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
}
