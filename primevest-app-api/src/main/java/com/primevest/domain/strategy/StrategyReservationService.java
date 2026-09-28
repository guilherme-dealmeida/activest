package com.primevest.domain.strategy;

import com.primevest.domain.strategy.dto.RebalanceDTO.ReservationRebalance;
import com.primevest.domain.strategy.dto.StrategyReservationDTO;
import com.primevest.domain.strategy.dto.StrategyReservationUpdateDTO;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Estrategia da reserva de emergencia.
 *
 * <p>A reserva concorre com o resto da carteira: somam-se as posicoes
 * marcadas como reserva ({@code is_reservation}) e o restante e a geracao de
 * renda. As regras do escopo:</p>
 *
 * <ul>
 *   <li><b>A</b> — uma estrategia por usuario (unicidade no banco);</li>
 *   <li><b>B</b> — nao se apaga: nao ha metodo de exclusao, e a leitura cria a
 *   linha padrao se ela faltar;</li>
 *   <li><b>C</b> — dois tipos: por percentual ou por valor, cada um com o seu
 *   alvo.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class StrategyReservationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    private final StrategyReservationRepository repository;
    private final UserRepository userRepository;
    private final StrategyHoldings holdings;

    public StrategyReservationService(StrategyReservationRepository repository,
                                      UserRepository userRepository,
                                      StrategyHoldings holdings) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.holdings = holdings;
    }

    /**
     * A estrategia do usuario e como a carteira esta em relacao a ela.
     *
     * <p>Escreve quando a linha falta: o usuario semeado e os cadastrados pela
     * API ja nascem com ela, mas um usuario anterior a esta feature nao — e a
     * regra B diz que ela sempre existe.</p>
     */
    @Transactional
    public StrategyReservationDTO get(UUID userId) {
        return toDTO(getOrCreate(userId), holdings.of(userId));
    }

    /**
     * Troca o tipo e o alvo (regra C).
     *
     * <p>So o alvo do tipo escolhido e gravado; o outro vira nulo. E o que a
     * checagem ck_strategy_reservations_target exige, e o que impede que um
     * alvo velho, de quando o usuario usava o outro tipo, volte a valer.</p>
     */
    @Transactional
    public StrategyReservationDTO update(UUID userId, StrategyReservationUpdateDTO dto) {
        StrategyReservation strategy = getOrCreate(userId);

        switch (dto.typeReservation()) {
            case PERCENTAGE -> {
                if (dto.targetPercentage() == null) {
                    throw new BusinessException("Informe o percentual ideal da reserva de emergência");
                }
                strategy.setTargetPercentage(dto.targetPercentage().setScale(2, RoundingMode.HALF_UP));
                strategy.setTargetValue(null);
            }
            case VALUE -> {
                if (dto.targetValue() == null) {
                    throw new BusinessException("Informe o valor ideal da reserva de emergência");
                }
                strategy.setTargetValue(dto.targetValue().setScale(2, RoundingMode.HALF_UP));
                strategy.setTargetPercentage(null);
            }
        }
        strategy.setTypeReservation(dto.typeReservation());
        repository.save(strategy);

        return toDTO(strategy, holdings.of(userId));
    }

    /**
     * Linha de rebalanceamento da reserva, para a tabela da aba
     * Rebalanceamento.
     *
     * <p>Nao grava nada: roda dentro da leitura do rebalanceamento, que e
     * somente leitura. Sem linha no banco, usa o padrao do nascimento em
     * memoria — a proxima abertura da aba "Estrategia Reserva" a cria.</p>
     */
    public ReservationRebalance rebalance(UUID userId, StrategyHoldings.Holdings current) {
        StrategyReservation strategy = repository.findByUserId(userId)
            .orElseGet(() -> StrategyReservation.defaultFor(null));

        BigDecimal total = current.total();
        BigDecimal reserve = current.reservation();
        BigDecimal currentPct = percentageOf(reserve, total);
        BigDecimal idealPct = idealPercentage(strategy, total);
        BigDecimal toContribute = amountToContribute(strategy, total, reserve);

        return new ReservationRebalance(
            strategy.getTypeReservation(),
            strategy.getTypeReservation() == ReservationType.PERCENTAGE ? targetPercentageOf(strategy) : null,
            strategy.getTypeReservation() == ReservationType.VALUE ? targetValueOf(strategy) : null,
            reserve,
            toContribute,
            reserve.add(toContribute),
            currentPct,
            currentPct.subtract(idealPct)
        );
    }

    /**
     * Quanto aportar na reserva para ela chegar ao alvo, sem vender nada.
     *
     * <p><b>Por percentual</b>: o aporte entra no total da carteira, entao nao
     * basta completar {@code p x total}. Com {@code p} em fracao, o aporte
     * {@code x} que faz {@code (reserva + x) / (total + x) = p} e
     * {@code x = (p x total - reserva) / (1 - p)}. Depois dele a reserva bate
     * o percentual exato.</p>
     *
     * <p>Com {@code p = 100%} a formula divide por zero: enquanto houver
     * geracao de renda, nenhum aporte finito leva a reserva a 100%. O aporte
     * vira {@code total - reserva} — igualar a reserva ao que a carteira vale
     * hoje, o maximo que faz sentido sugerir sem vender.</p>
     *
     * <p><b>Por valor</b>: o alvo e absoluto, e o total nao entra.
     * {@code x = alvo - reserva}.</p>
     *
     * <p>Negativo quando a reserva ja esta acima do alvo: a tela mostra o
     * tamanho do excesso em vermelho e nao manda vender.</p>
     */
    BigDecimal amountToContribute(StrategyReservation strategy, BigDecimal total, BigDecimal reserve) {
        BigDecimal amount;
        if (strategy.getTypeReservation() == ReservationType.VALUE) {
            amount = targetValueOf(strategy).subtract(reserve);
        } else {
            BigDecimal p = targetPercentageOf(strategy).divide(HUNDRED, 10, RoundingMode.HALF_UP);
            if (p.compareTo(BigDecimal.ONE) >= 0) {
                amount = total.subtract(reserve);
            } else {
                amount = p.multiply(total).subtract(reserve)
                    .divide(BigDecimal.ONE.subtract(p), 10, RoundingMode.HALF_UP);
            }
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        // So arredondamento produz um centavo de diferenca numa reserva que
        // ja esta no alvo; mostrar "aportar R$ 0,01" seria ruido.
        return amount.abs().compareTo(ONE_CENT) <= 0 ? BigDecimal.ZERO.setScale(2) : amount;
    }

    /**
     * O percentual que o % diff compara. No tipo VALUE e o alvo sobre a
     * carteira de hoje, limitado a 100%: um alvo maior que a carteira inteira
     * nao faz a reserva "precisar" de mais de 100% dela.
     */
    private BigDecimal idealPercentage(StrategyReservation strategy, BigDecimal total) {
        if (strategy.getTypeReservation() == ReservationType.PERCENTAGE) {
            return targetPercentageOf(strategy);
        }
        return percentageOf(targetValueOf(strategy), total).min(HUNDRED);
    }

    private StrategyReservation getOrCreate(UUID userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            return repository.save(StrategyReservation.defaultFor(user));
        });
    }

    private StrategyReservationDTO toDTO(StrategyReservation strategy, StrategyHoldings.Holdings current) {
        BigDecimal total = current.total();
        BigDecimal reserve = current.reservation();
        BigDecimal currentPct = percentageOf(reserve, total);
        BigDecimal idealPct = idealPercentage(strategy, total);
        return new StrategyReservationDTO(
            strategy.getId(),
            strategy.getTypeReservation(),
            strategy.getTargetPercentage(),
            strategy.getTargetValue(),
            total,
            reserve,
            total.subtract(reserve),
            currentPct,
            idealPct,
            currentPct.subtract(idealPct)
        );
    }

    private static BigDecimal targetPercentageOf(StrategyReservation s) {
        return s.getTargetPercentage() != null ? s.getTargetPercentage() : BigDecimal.ZERO;
    }

    private static BigDecimal targetValueOf(StrategyReservation s) {
        return s.getTargetValue() != null ? s.getTargetValue() : BigDecimal.ZERO;
    }

    private static BigDecimal percentageOf(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }
}
