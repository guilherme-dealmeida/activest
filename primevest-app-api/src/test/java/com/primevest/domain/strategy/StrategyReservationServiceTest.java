package com.primevest.domain.strategy;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.portfolio.Position;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.strategy.dto.RebalanceDTO;
import com.primevest.domain.strategy.dto.StrategyReservationDTO;
import com.primevest.domain.strategy.dto.StrategyReservationUpdateDTO;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
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
    void cenario() {
        userId = UUID.randomUUID();
    }

    /** Carteira com uma posicao de reserva e uma de geracao de renda. */
    private StrategyHoldings.Holdings carteira(String reserva, String geracaoDeRenda) {
        AssetType rendaFixa = AssetType.builder().name("RENDA_FIXA").description("Renda Fixa").fixedIncome(true).build();
        AssetType acao = AssetType.builder().name("ACAO").description("Ações").build();

        PortfolioAsset posicaoReserva = PortfolioAsset.builder()
            .asset(Asset.builder().ticker("CDB").assetType(rendaFixa).build())
            .isReservation(true)
            .build();
        posicaoReserva.setId(UUID.randomUUID());

        PortfolioAsset posicaoRenda = PortfolioAsset.builder()
            .asset(Asset.builder().ticker("PETR4").assetType(acao).build())
            .build();
        posicaoRenda.setId(UUID.randomUUID());

        Map<UUID, Position> posicoes = new HashMap<>();
        posicoes.put(posicaoReserva.getId(), new Position(null, null, new BigDecimal(reserva), BigDecimal.ZERO));
        posicoes.put(posicaoRenda.getId(), new Position(null, null, new BigDecimal(geracaoDeRenda), BigDecimal.ZERO));

        return new StrategyHoldings.Holdings(List.of(posicaoReserva, posicaoRenda), posicoes);
    }

    private StrategyReservation porPercentual(String p) {
        return StrategyReservation.builder()
            .typeReservation(ReservationType.PERCENTAGE)
            .targetPercentage(new BigDecimal(p))
            .build();
    }

    private StrategyReservation porValor(String v) {
        return StrategyReservation.builder()
            .typeReservation(ReservationType.VALUE)
            .targetValue(new BigDecimal(v))
            .build();
    }

    @Test
    @DisplayName("Por percentual: carteira 10.000 com reserva 2.000 e ideal 30% pede 1.428,57")
    void aportePorPercentual() {
        BigDecimal aporte = service.amountToContribute(porPercentual("30.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(aporte).isEqualByComparingTo("1428.57");
    }

    @Test
    @DisplayName("Depois do aporte sugerido a reserva bate o percentual exato")
    void aporteLevaAoAlvoExato() {
        BigDecimal total = new BigDecimal("10000.00");
        BigDecimal reserva = new BigDecimal("2000.00");
        BigDecimal aporte = service.amountToContribute(porPercentual("30.00"), total, reserva);

        BigDecimal reservaDepois = reserva.add(aporte);
        BigDecimal totalDepois = total.add(aporte);

        assertThat(reservaDepois.multiply(new BigDecimal("100"))
            .divide(totalDepois, 2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Por valor: ideal R$ 3.000 com reserva 2.000 pede 1.000 — o total nao entra")
    void aportePorValor() {
        BigDecimal aporte = service.amountToContribute(porValor("3000.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(aporte).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Reserva acima do alvo da aporte negativo: mostra o excesso, nao manda vender")
    void aporteNegativo() {
        BigDecimal aporte = service.amountToContribute(porPercentual("10.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(aporte).isEqualByComparingTo("-1111.11");
    }

    @Test
    @DisplayName("Ideal de 100% nao divide por zero: o aporte e o que falta para igualar a carteira")
    void alvoDeCemPorCento() {
        BigDecimal aporte = service.amountToContribute(porPercentual("100.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(aporte).isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("Reserva ja no alvo nao vira 'aportar R$ 0,01'")
    void reservaNoAlvoNaoPedeCentavo() {
        BigDecimal aporte = service.amountToContribute(porPercentual("20.00"),
            new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        assertThat(aporte).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Carteira vazia com alvo por percentual nao pede aporte")
    void carteiraVazia() {
        BigDecimal aporte = service.amountToContribute(porPercentual("30.00"),
            BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(aporte).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("No tipo valor, o % ideal e o alvo sobre a carteira, limitado a 100%")
    void percentualIdealNoTipoValorNaoPassaDeCem() {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(porValor("30000.00")));

        RebalanceDTO.ReservationRebalance linha = service.rebalance(userId, carteira("2000.00", "8000.00"));

        // alvo 30.000 numa carteira de 10.000 daria 300%; o diff usa 100%.
        assertThat(linha.currentPercentage()).isEqualByComparingTo("20.00");
        assertThat(linha.diffPercentage()).isEqualByComparingTo("-80.00");
        assertThat(linha.amountToRebalance()).isEqualByComparingTo("28000.00");
    }

    @Test
    @DisplayName("O rebalanceamento nao grava a linha padrao de quem ainda nao tem estrategia de reserva")
    void rebalanceNaoGrava() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());

        RebalanceDTO.ReservationRebalance linha = service.rebalance(userId, carteira("2000.00", "8000.00"));

        assertThat(linha.typeReservation()).isEqualTo(ReservationType.PERCENTAGE);
        assertThat(linha.currentValue()).isEqualByComparingTo("2000.00");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("A leitura da aba Estrategia Reserva cria a linha padrao de quem nao tem")
    void getCriaALinhaPadrao() {
        User user = User.builder().email("dono@primevest.test").build();
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(any(StrategyReservation.class))).thenAnswer(i -> i.getArgument(0));
        when(holdings.of(userId)).thenReturn(carteira("2000.00", "8000.00"));

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
    void trocaDeTipoLimpaOAlvoAntigo() {
        StrategyReservation existente = porPercentual("30.00");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existente));
        when(holdings.of(userId)).thenReturn(carteira("2000.00", "8000.00"));

        service.update(userId, new StrategyReservationUpdateDTO(ReservationType.VALUE, null, new BigDecimal("30000")));

        assertThat(existente.getTypeReservation()).isEqualTo(ReservationType.VALUE);
        assertThat(existente.getTargetValue()).isEqualByComparingTo("30000.00");
        assertThat(existente.getTargetPercentage()).isNull();
    }

    @Test
    @DisplayName("Tipo percentual sem percentual, e tipo valor sem valor, sao recusados")
    void alvoObrigatorioPorTipo() {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(porPercentual("30.00")));

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
    void posicaoLiquidadaNaoConta() {
        StrategyHoldings.Holdings comLiquidada = carteira("2000.00", "8000.00");
        comLiquidada.assets().get(0).setSettled(true);

        assertThat(comLiquidada.total()).isEqualByComparingTo("8000.00");
        assertThat(comLiquidada.reservation()).isEqualByComparingTo("0.00");
    }
}
