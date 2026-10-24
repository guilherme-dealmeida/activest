package com.activest.domain.strategy;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetType;
import com.activest.domain.portfolio.Position;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.strategy.dto.RebalanceDTO;
import com.activest.domain.strategy.dto.StrategyReservationDTO;
import com.activest.domain.strategy.dto.StrategyReservationUpdateDTO;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import com.activest.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Os numeros da reserva de emergencia.
 *
 * <p>O exemplo conferido de ponta a ponta no Claude.md e a carteira de
 * R$ 10.000 com R$ 2.000 de reserva (20%): ideal 30% pede R$ 1.428,57,
 * ideal R$ 3.000 pede R$ 1.000 e ideal 10% da −R$ 1.111,11.</p>
 */
@ExtendWith(MockitoExtension.class)
class StrategyReservationServiceTest {

    @Mock private StrategyReservationRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private StrategyHoldings holdings;

    @InjectMocks private StrategyReservationService service;

    private UUID userId;

    @BeforeEach
    void scenario() {
        userId = UUID.randomUUID();
    }

    /** Carteira com uma posicao de reserva e uma de geracao de renda. */
    private StrategyHoldings.Holdings portfolioEntity(String reserve, String incomeGeneration) {
        AssetType fixedIncomeType = AssetType.builder().name("RENDA_FIXA").description("Renda Fixa").fixedIncome(true).build();
        AssetType stock = AssetType.builder().name("ACAO").description("Ações").build();

        PortfolioAsset reservePosition = PortfolioAsset.builder()
            .asset(Asset.builder().ticker("CDB").assetType(fixedIncomeType).build())
            .isReservation(true)
            .build();
        reservePosition.setId(UUID.randomUUID());

        PortfolioAsset incomePosition = PortfolioAsset.builder()
            .asset(Asset.builder().ticker("PETR4").assetType(stock).build())
            .build();
        incomePosition.setId(UUID.randomUUID());

        Map<UUID, Position> positions = new HashMap<>();
        positions.put(reservePosition.getId(), new Position(null, null, new BigDecimal(reserve), BigDecimal.ZERO));
        positions.put(incomePosition.getId(), new Position(null, null, new BigDecimal(incomeGeneration), BigDecimal.ZERO));

        return new StrategyHoldings.Holdings(List.of(reservePosition, incomePosition), positions);
    }

    private StrategyReservation byPercentage(String p) {
        return StrategyReservation.builder()
            .typeReservation(ReservationType.PERCENTAGE)
            .targetPercentage(new BigDecimal(p))
            .build();
    }

    private StrategyReservation byValue(String v) {
        return StrategyReservation.builder()
            .typeReservation(ReservationType.VALUE)
            .targetValue(new BigDecimal(v))
            .build();
    }

    @Test
    @DisplayName("Por percentual: carteira 10.000 com reserva 2.000 e ideal 30% pede 1.428,57")
    void contributionByPercentage() {
        BigDecimal contribution = service.amountToContribute(byPercentage("30.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(contribution).isEqualByComparingTo("1428.57");
    }

    @Test
    @DisplayName("Depois do aporte sugerido a reserva bate o percentual exato")
    void contributionReachesExactTarget() {
        BigDecimal total = new BigDecimal("10000.00");
        BigDecimal reserve = new BigDecimal("2000.00");
        BigDecimal contribution = service.amountToContribute(byPercentage("30.00"), total, reserve);

        BigDecimal reserveAfter = reserve.add(contribution);
        BigDecimal totalAfter = total.add(contribution);

        assertThat(reserveAfter.multiply(new BigDecimal("100"))
            .divide(totalAfter, 2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Por valor: ideal R$ 3.000 com reserva 2.000 pede 1.000 — o total nao entra")
    void contributionByValue() {
        BigDecimal contribution = service.amountToContribute(byValue("3000.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(contribution).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Reserva acima do alvo da aporte negativo: mostra o excesso, nao manda vender")
    void negativeContribution() {
        BigDecimal contribution = service.amountToContribute(byPercentage("10.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(contribution).isEqualByComparingTo("-1111.11");
    }

    @Test
    @DisplayName("Ideal de 100% nao divide por zero: o aporte e o que falta para igualar a carteira")
    void hundredPercentTarget() {
        BigDecimal contribution = service.amountToContribute(byPercentage("100.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(contribution).isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("Reserva ja no alvo nao vira 'aportar R$ 0,01'")
    void reserveAtTargetAsksNoCent() {
        BigDecimal contribution = service.amountToContribute(byPercentage("20.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(contribution).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Carteira vazia com alvo por percentual nao pede aporte")
    void emptyPortfolio() {
        BigDecimal contribution = service.amountToContribute(byPercentage("30.00"),
            BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(contribution).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("No tipo valor, o % ideal e o alvo sobre a carteira, limitado a 100%")
    void idealPercentageInValueTypeNotAboveHundred() {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(byValue("30000.00")));

        RebalanceDTO.ReservationRebalance row = service.rebalance(userId, portfolioEntity("2000.00", "8000.00"));

        // alvo 30.000 numa carteira de 10.000 daria 300%; o diff usa 100%.
        assertThat(row.currentPercentage()).isEqualByComparingTo("20.00");
        assertThat(row.diffPercentage()).isEqualByComparingTo("-80.00");
        assertThat(row.amountToRebalance()).isEqualByComparingTo("28000.00");
    }

    @Test
    @DisplayName("O rebalanceamento nao grava a linha padrao de quem ainda nao tem estrategia de reserva")
    void rebalanceDoesNotStore() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());

        RebalanceDTO.ReservationRebalance row = service.rebalance(userId, portfolioEntity("2000.00", "8000.00"));

        assertThat(row.typeReservation()).isEqualTo(ReservationType.PERCENTAGE);
        assertThat(row.currentValue()).isEqualByComparingTo("2000.00");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("A leitura da aba Estrategia Reserva cria a linha padrao de quem nao tem")
    void getCreatesDefaultRow() {
        User user = User.builder().email("dono@activest.test").build();
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(any(StrategyReservation.class))).thenAnswer(i -> i.getArgument(0));
        when(holdings.of(userId)).thenReturn(portfolioEntity("2000.00", "8000.00"));

        StrategyReservationDTO dto = service.get(userId);

        verify(repository).save(any(StrategyReservation.class));
        assertThat(dto.typeReservation()).isEqualTo(ReservationType.PERCENTAGE);
        assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("10000.00");
        assertThat(dto.reservationValue()).isEqualByComparingTo("2000.00");
        assertThat(dto.incomeValue()).isEqualByComparingTo("8000.00");
        assertThat(dto.currentPercentage()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Trocar para o tipo valor limpa o percentual, e vice-versa")
    void typeChangeClearsOldTarget() {
        StrategyReservation existing = byPercentage("30.00");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(holdings.of(userId)).thenReturn(portfolioEntity("2000.00", "8000.00"));

        service.update(userId, new StrategyReservationUpdateDTO(ReservationType.VALUE, null, new BigDecimal("30000")));

        assertThat(existing.getTypeReservation()).isEqualTo(ReservationType.VALUE);
        assertThat(existing.getTargetValue()).isEqualByComparingTo("30000.00");
        assertThat(existing.getTargetPercentage()).isNull();
    }

    @Test
    @DisplayName("Tipo percentual sem percentual, e tipo valor sem valor, sao recusados")
    void targetRequiredByType() {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(byPercentage("30.00")));

        assertThatThrownBy(() -> service.update(userId,
            new StrategyReservationUpdateDTO(ReservationType.PERCENTAGE, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Informe o percentual ideal da reserva de emergência");

        assertThatThrownBy(() -> service.update(userId,
            new StrategyReservationUpdateDTO(ReservationType.VALUE, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Informe o valor ideal da reserva de emergência");
    }

    @Test
    @DisplayName("Posicao liquidada nao conta na reserva nem no total")
    void settledPositionDoesNotCount() {
        StrategyHoldings.Holdings withSettled = portfolioEntity("2000.00", "8000.00");
        withSettled.assets().get(0).setSettled(true);

        assertThat(withSettled.total()).isEqualByComparingTo("8000.00");
        assertThat(withSettled.reservation()).isEqualByComparingTo("0.00");
    }
}
