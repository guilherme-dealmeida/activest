package com.primevest.domain.strategy;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.AssetTypeRepository;
import com.primevest.domain.portfolio.Position;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.strategy.dto.RebalanceDTO;
import com.primevest.domain.strategy.dto.StrategyUpdateDTO;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrategyServiceTest {

    @Mock private StrategyRepository strategyRepository;
    @Mock private AssetTypeRepository assetTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private StrategyHoldings strategyHoldings;
    @Mock private StrategyReservationService strategyReservationService;

    @InjectMocks private StrategyService service;

    private UUID userId;
    private User user;
    private AssetType acao;
    private AssetType fii;
    private AssetType rendaFixa;

    @BeforeEach
    void cenario() {
        userId = UUID.randomUUID();
        user = User.builder().email("dono@primevest.test").build();
        acao = tipo(1L, "ACAO", "Ações", false);
        fii = tipo(2L, "FII", "FIIs", false);
        rendaFixa = tipo(3L, "RENDA_FIXA", "Renda Fixa", true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(assetTypeRepository.findById(1L)).thenReturn(Optional.of(acao));
        when(assetTypeRepository.findById(2L)).thenReturn(Optional.of(fii));
        when(assetTypeRepository.findById(3L)).thenReturn(Optional.of(rendaFixa));
        when(assetTypeRepository.findAllByOrderByIdAsc()).thenReturn(List.of(acao, fii, rendaFixa));
        when(strategyRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AssetType tipo(Long id, String name, String description, boolean fixedIncome) {
        AssetType type = AssetType.builder().name(name).description(description).fixedIncome(fixedIncome).build();
        type.setId(id);
        return type;
    }

    private static StrategyUpdateDTO pedido(Object... paresTipoPercentual) {
        List<StrategyUpdateDTO.StrategyItemDTO> items = new ArrayList<>();
        for (int i = 0; i < paresTipoPercentual.length; i += 2) {
            items.add(new StrategyUpdateDTO.StrategyItemDTO(
                (Long) paresTipoPercentual[i], new BigDecimal((String) paresTipoPercentual[i + 1])));
        }
        return new StrategyUpdateDTO(items);
    }

    @Nested
    @DisplayName("PUT /strategy — o unico caminho de escrita")
    class Atualizacao {

        @Test
        @DisplayName("Full replace: apaga o que existia e grava o que veio")
        void fullReplace() {
            List<Strategy> antigas = new ArrayList<>(List.of(Strategy.builder()
                .user(user).assetType(rendaFixa).targetPercentage(new BigDecimal("100.00")).build()));
            when(strategyRepository.findByUserId(userId)).thenReturn(antigas);

            service.updateStrategy(userId, pedido(1L, "60.00", 2L, "40.00"));

            verify(strategyRepository).deleteAll(antigas);
            verify(strategyRepository, org.mockito.Mockito.times(2)).save(any(Strategy.class));
        }

        @Test
        @DisplayName("F7 — a soma tem que ser 100%")
        void somaExataDeCem() {
            assertThatThrownBy(() -> service.updateStrategy(userId, pedido(1L, "60.00", 2L, "30.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A soma dos percentuais ideais deve ser 100%")
                .hasMessageContaining("90");

            verify(strategyRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("F7 — 33,33 tres vezes (99,99) passa: o arredondamento de duas casas nao fecha exato")
        void toleranciaDeArredondamento() {
            service.updateStrategy(userId, pedido(1L, "33.33", 2L, "33.33", 3L, "33.33"));

            verify(strategyRepository, org.mockito.Mockito.times(3)).save(any(Strategy.class));
        }

        @Test
        @DisplayName("F7 — a tolerancia e a da tela: 99,95 a 100,04 passam, 99,94 e 100,05 nao")
        void limitesDaTolerancia() {
            service.updateStrategy(userId, pedido(1L, "99.95"));
            service.updateStrategy(userId, pedido(1L, "100.04"));

            assertThatThrownBy(() -> service.updateStrategy(userId, pedido(1L, "99.94")))
                .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.updateStrategy(userId, pedido(1L, "100.05")))
                .isInstanceOf(BusinessException.class);
        }


        @Test
        @DisplayName("F7 — lista vazia nao se salva")
        void listaVaziaNaoSalva() {
            assertThatThrownBy(() -> service.updateStrategy(userId, new StrategyUpdateDTO(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A soma dos percentuais ideais deve ser 100%");

            verify(strategyRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("F3 — o mesmo tipo duas vezes e recusado com mensagem, e nao com erro de banco")
        void tipoRepetido() {
            assertThatThrownBy(() -> service.updateStrategy(userId, pedido(1L, "60.00", 1L, "40.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O tipo Ações aparece mais de uma vez na estratégia.");

            verify(strategyRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("F6 — tipo de ativo inexistente responde 'nao encontrado'")
        void tipoInexistente() {
            when(assetTypeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStrategy(userId, pedido(99L, "100.00")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tipo de ativo não encontrado: 99");
        }

        @Test
        @DisplayName("Usuario inexistente responde 'nao encontrado'")
        void usuarioInexistente() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStrategy(userId, pedido(1L, "100.00")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
        }
    }

    @Nested
    @DisplayName("GET /strategy/rebalance")
    class Rebalanceamento {

        private PortfolioAsset posicao(AssetType tipo, boolean reserva) {
            PortfolioAsset pa = PortfolioAsset.builder()
                .asset(Asset.builder().ticker("X").assetType(tipo).build())
                .isReservation(reserva)
                .build();
            pa.setId(UUID.randomUUID());
            return pa;
        }

        /** Carteira montada como pares (tipo, valor, e reserva?). */
        private StrategyHoldings.Holdings carteira(Object... linhas) {
            List<PortfolioAsset> assets = new ArrayList<>();
            Map<UUID, Position> posicoes = new HashMap<>();
            for (int i = 0; i < linhas.length; i += 3) {
                PortfolioAsset pa = posicao((AssetType) linhas[i], (Boolean) linhas[i + 2]);
                assets.add(pa);
                posicoes.put(pa.getId(),
                    new Position(null, null, new BigDecimal((String) linhas[i + 1]), BigDecimal.ZERO));
            }
            return new StrategyHoldings.Holdings(assets, posicoes);
        }

        private RebalanceDTO rebalancear(StrategyHoldings.Holdings carteira, List<Strategy> estrategias) {
            when(strategyRepository.findByUserId(userId)).thenReturn(estrategias);
            when(strategyHoldings.of(userId)).thenReturn(carteira);
            return service.getRebalanceSuggestion(userId);
        }

        private RebalanceDTO.RebalanceItem item(RebalanceDTO dto, String tipo) {
            return dto.items().stream().filter(i -> i.typeName().equals(tipo)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("Regra A — a base e o tipo mais acima do ideal, e ele fica com sugestao zero")
        void regraA_baseEOTipoMaisAcimaDoIdeal() {
            RebalanceDTO dto = rebalancear(
                carteira(acao, "6000.00", false, fii, "2000.00", false),
                List.of(Strategy.builder().user(user).assetType(acao).targetPercentage(new BigDecimal("50.00")).build(),
                        Strategy.builder().user(user).assetType(fii).targetPercentage(new BigDecimal("50.00")).build()));

            // Acao: 6000/50 = 120 por ponto (a maior razao) -> base.
            assertThat(item(dto, "Ações").amountToRebalance()).isEqualByComparingTo("0.00");
            assertThat(item(dto, "FIIs").amountToRebalance()).isEqualByComparingTo("4000.00");
            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("Regra A — carteira ja equilibrada zera todas as sugestoes")
        void regraA_carteiraEquilibrada() {
            RebalanceDTO dto = rebalancear(
                carteira(acao, "5000.00", false, fii, "5000.00", false),
                List.of(Strategy.builder().user(user).assetType(acao).targetPercentage(new BigDecimal("50.00")).build(),
                        Strategy.builder().user(user).assetType(fii).targetPercentage(new BigDecimal("50.00")).build()));

            assertThat(dto.items()).allSatisfy(i ->
                assertThat(i.amountToRebalance()).isEqualByComparingTo("0.00"));
        }

        @Test
        @DisplayName("Regra B — posicao de tipo sem ideal cadastrado fica fora do total")
        void regraB_tipoSemIdealForaDaConta() {
            RebalanceDTO dto = rebalancear(
                carteira(acao, "5000.00", false, fii, "5000.00", false),
                List.of(Strategy.builder().user(user).assetType(acao).targetPercentage(new BigDecimal("100.00")).build()));

            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("5000.00");
            assertThat(dto.items()).hasSize(1);
            assertThat(item(dto, "Ações").currentPercentage()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Regra C — posicao de reserva de emergencia nunca entra no rebalanceamento por tipo")
        void regraC_reservaForaDaConta() {
            RebalanceDTO dto = rebalancear(
                carteira(acao, "5000.00", false, rendaFixa, "3000.00", true),
                List.of(Strategy.builder().user(user).assetType(acao).targetPercentage(new BigDecimal("50.00")).build(),
                        Strategy.builder().user(user).assetType(rendaFixa).targetPercentage(new BigDecimal("50.00")).build()));

            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("5000.00");
            assertThat(item(dto, "Renda Fixa").currentValue()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Sem estrategia cadastrada, a resposta e so a distribuicao atual, com ideal zero")
        void semEstrategiaMostraDistribuicaoAtual() {
            RebalanceDTO dto = rebalancear(
                carteira(acao, "7500.00", false, fii, "2500.00", false, rendaFixa, "1000.00", true),
                List.of());

            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("10000.00");
            assertThat(item(dto, "Ações").currentPercentage()).isEqualByComparingTo("75.00");
            assertThat(item(dto, "Ações").idealPercentage()).isEqualByComparingTo("0.00");
            // Regra C vale aqui tambem: a reserva de 1.000 ficou de fora.
            assertThat(item(dto, "Renda Fixa").currentValue()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Tipo com ideal zero nao serve de base, e com dinheiro dentro da sugestao negativa")
        void tipoComIdealZero() {
            RebalanceDTO dto = rebalancear(
                carteira(acao, "5000.00", false, fii, "1000.00", false),
                List.of(Strategy.builder().user(user).assetType(acao).targetPercentage(new BigDecimal("100.00")).build(),
                        Strategy.builder().user(user).assetType(fii).targetPercentage(BigDecimal.ZERO).build()));

            assertThat(item(dto, "Ações").amountToRebalance()).isEqualByComparingTo("0.00");
            assertThat(item(dto, "FIIs").amountToRebalance()).isEqualByComparingTo("-1000.00");
        }

        @Test
        @DisplayName("A linha da reserva vem do StrategyReservationService, fora dos itens por tipo")
        void reservaEUmaLinhaPropria() {
            RebalanceDTO.ReservationRebalance linha = new RebalanceDTO.ReservationRebalance(
                ReservationType.PERCENTAGE, new BigDecimal("30.00"), null, new BigDecimal("3000.00"),
                new BigDecimal("1428.57"), new BigDecimal("4428.57"), new BigDecimal("20.00"),
                new BigDecimal("-10.00"));
            when(strategyReservationService.rebalance(any(), any())).thenReturn(linha);

            RebalanceDTO dto = rebalancear(
                carteira(acao, "5000.00", false),
                List.of(Strategy.builder().user(user).assetType(acao).targetPercentage(new BigDecimal("100.00")).build()));

            assertThat(dto.reservation()).isSameAs(linha);
        }
    }
}
