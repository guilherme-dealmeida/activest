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
    private AssetType stock;
    private AssetType fii;
    private AssetType fixedIncomeType;

    @BeforeEach
    void scenario() {
        userId = UUID.randomUUID();
        user = User.builder().email("dono@primevest.test").build();
        stock = typeValue(1L, "ACAO", "Ações", false);
        fii = typeValue(2L, "FII", "FIIs", false);
        fixedIncomeType = typeValue(3L, "RENDA_FIXA", "Renda Fixa", true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(assetTypeRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(assetTypeRepository.findById(2L)).thenReturn(Optional.of(fii));
        when(assetTypeRepository.findById(3L)).thenReturn(Optional.of(fixedIncomeType));
        when(assetTypeRepository.findAllByOrderByIdAsc()).thenReturn(List.of(stock, fii, fixedIncomeType));
        when(strategyRepository.findByUserId(userId)).thenReturn(new ArrayList<>());
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AssetType typeValue(Long id, String name, String description, boolean fixedIncome) {
        AssetType type = AssetType.builder().name(name).description(description).fixedIncome(fixedIncome).build();
        type.setId(id);
        return type;
    }

    private static StrategyUpdateDTO request(Object... typePercentagePairs) {
        List<StrategyUpdateDTO.StrategyItemDTO> items = new ArrayList<>();
        for (int i = 0; i < typePercentagePairs.length; i += 2) {
            items.add(new StrategyUpdateDTO.StrategyItemDTO(
                (Long) typePercentagePairs[i], new BigDecimal((String) typePercentagePairs[i + 1])));
        }
        return new StrategyUpdateDTO(items);
    }

    @Nested
    @DisplayName("PUT /strategy — o unico caminho de escrita")
    class Update {

        @Test
        @DisplayName("Full replace: apaga o que existia e grava o que veio")
        void fullReplace() {
            List<Strategy> old = new ArrayList<>(List.of(Strategy.builder()
                .user(user).assetType(fixedIncomeType).targetPercentage(new BigDecimal("100.00")).build()));
            when(strategyRepository.findByUserId(userId)).thenReturn(old);

            service.updateStrategy(userId, request(1L, "60.00", 2L, "40.00"));

            verify(strategyRepository).deleteAll(old);
            verify(strategyRepository, org.mockito.Mockito.times(2)).save(any(Strategy.class));
        }

        @Test
        @DisplayName("F7 — a soma tem que ser 100%")
        void exactSumOfHundred() {
            assertThatThrownBy(() -> service.updateStrategy(userId, request(1L, "60.00", 2L, "30.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A soma dos percentuais ideais deve ser 100%")
                .hasMessageContaining("90");

            verify(strategyRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("F7 — 33,33 tres vezes (99,99) passa: o arredondamento de duas casas nao fecha exato")
        void roundingTolerance() {
            service.updateStrategy(userId, request(1L, "33.33", 2L, "33.33", 3L, "33.33"));

            verify(strategyRepository, org.mockito.Mockito.times(3)).save(any(Strategy.class));
        }

        @Test
        @DisplayName("F7 — a tolerancia e a da tela: 99,95 a 100,04 passam, 99,94 e 100,05 nao")
        void toleranceBounds() {
            service.updateStrategy(userId, request(1L, "99.95"));
            service.updateStrategy(userId, request(1L, "100.04"));

            assertThatThrownBy(() -> service.updateStrategy(userId, request(1L, "99.94")))
                .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.updateStrategy(userId, request(1L, "100.05")))
                .isInstanceOf(BusinessException.class);
        }


        @Test
        @DisplayName("F7 — lista vazia nao se salva")
        void emptyListNotSaved() {
            assertThatThrownBy(() -> service.updateStrategy(userId, new StrategyUpdateDTO(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A soma dos percentuais ideais deve ser 100%");

            verify(strategyRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("F3 — o mesmo tipo duas vezes e recusado com mensagem, e nao com erro de banco")
        void repeatedType() {
            assertThatThrownBy(() -> service.updateStrategy(userId, request(1L, "60.00", 1L, "40.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O tipo Ações aparece mais de uma vez na estratégia.");

            verify(strategyRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("F6 — tipo de ativo inexistente responde 'nao encontrado'")
        void missingType() {
            when(assetTypeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStrategy(userId, request(99L, "100.00")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tipo de ativo não encontrado: 99");
        }

        @Test
        @DisplayName("Usuario inexistente responde 'nao encontrado'")
        void missingUser() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStrategy(userId, request(1L, "100.00")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
        }
    }

    @Nested
    @DisplayName("GET /strategy/rebalance")
    class Rebalance {

        private PortfolioAsset position(AssetType typeValue, boolean reserve) {
            PortfolioAsset pa = PortfolioAsset.builder()
                .asset(Asset.builder().ticker("X").assetType(typeValue).build())
                .isReservation(reserve)
                .build();
            pa.setId(UUID.randomUUID());
            return pa;
        }

        /** Carteira montada como pares (tipo, valor, e reserva?). */
        private StrategyHoldings.Holdings portfolioEntity(Object... rows) {
            List<PortfolioAsset> assets = new ArrayList<>();
            Map<UUID, Position> positions = new HashMap<>();
            for (int i = 0; i < rows.length; i += 3) {
                PortfolioAsset pa = position((AssetType) rows[i], (Boolean) rows[i + 2]);
                assets.add(pa);
                positions.put(pa.getId(),
                    new Position(null, null, new BigDecimal((String) rows[i + 1]), BigDecimal.ZERO));
            }
            return new StrategyHoldings.Holdings(assets, positions);
        }

        private RebalanceDTO rebalancear(StrategyHoldings.Holdings portfolioEntity, List<Strategy> strategies) {
            when(strategyRepository.findByUserId(userId)).thenReturn(strategies);
            when(strategyHoldings.of(userId)).thenReturn(portfolioEntity);
            return service.getRebalanceSuggestion(userId);
        }

        private RebalanceDTO.RebalanceItem item(RebalanceDTO dto, String typeValue) {
            return dto.items().stream().filter(i -> i.typeName().equals(typeValue)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("Regra A — a base e o tipo mais acima do ideal, e ele fica com sugestao zero")
        void ruleA_baseIsTypeMostAboveIdeal() {
            RebalanceDTO dto = rebalancear(
                portfolioEntity(stock, "6000.00", false, fii, "2000.00", false),
                List.of(Strategy.builder().user(user).assetType(stock).targetPercentage(new BigDecimal("50.00")).build(),
                        Strategy.builder().user(user).assetType(fii).targetPercentage(new BigDecimal("50.00")).build()));

            // Acao: 6000/50 = 120 por ponto (a maior razao) -> base.
            assertThat(item(dto, "Ações").amountToRebalance()).isEqualByComparingTo("0.00");
            assertThat(item(dto, "FIIs").amountToRebalance()).isEqualByComparingTo("4000.00");
            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("Regra A — carteira ja equilibrada zera todas as sugestoes")
        void ruleA_balancedPortfolio() {
            RebalanceDTO dto = rebalancear(
                portfolioEntity(stock, "5000.00", false, fii, "5000.00", false),
                List.of(Strategy.builder().user(user).assetType(stock).targetPercentage(new BigDecimal("50.00")).build(),
                        Strategy.builder().user(user).assetType(fii).targetPercentage(new BigDecimal("50.00")).build()));

            assertThat(dto.items()).allSatisfy(i ->
                assertThat(i.amountToRebalance()).isEqualByComparingTo("0.00"));
        }

        @Test
        @DisplayName("Regra B — posicao de tipo sem ideal cadastrado fica fora do total")
        void ruleB_typeWithoutIdealExcluded() {
            RebalanceDTO dto = rebalancear(
                portfolioEntity(stock, "5000.00", false, fii, "5000.00", false),
                List.of(Strategy.builder().user(user).assetType(stock).targetPercentage(new BigDecimal("100.00")).build()));

            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("5000.00");
            assertThat(dto.items()).hasSize(1);
            assertThat(item(dto, "Ações").currentPercentage()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Regra C — posicao de reserva de emergencia nunca entra no rebalanceamento por tipo")
        void ruleC_reserveExcluded() {
            RebalanceDTO dto = rebalancear(
                portfolioEntity(stock, "5000.00", false, fixedIncomeType, "3000.00", true),
                List.of(Strategy.builder().user(user).assetType(stock).targetPercentage(new BigDecimal("50.00")).build(),
                        Strategy.builder().user(user).assetType(fixedIncomeType).targetPercentage(new BigDecimal("50.00")).build()));

            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("5000.00");
            assertThat(item(dto, "Renda Fixa").currentValue()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Sem estrategia cadastrada, a resposta e so a distribuicao atual, com ideal zero")
        void withoutStrategyShowsCurrentDistribution() {
            RebalanceDTO dto = rebalancear(
                portfolioEntity(stock, "7500.00", false, fii, "2500.00", false, fixedIncomeType, "1000.00", true),
                List.of());

            assertThat(dto.totalPortfolioValue()).isEqualByComparingTo("10000.00");
            assertThat(item(dto, "Ações").currentPercentage()).isEqualByComparingTo("75.00");
            assertThat(item(dto, "Ações").idealPercentage()).isEqualByComparingTo("0.00");
            // Regra C vale aqui tambem: a reserva de 1.000 ficou de fora.
            assertThat(item(dto, "Renda Fixa").currentValue()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Tipo com ideal zero nao serve de base, e com dinheiro dentro da sugestao negativa")
        void typeWithZeroIdeal() {
            RebalanceDTO dto = rebalancear(
                portfolioEntity(stock, "5000.00", false, fii, "1000.00", false),
                List.of(Strategy.builder().user(user).assetType(stock).targetPercentage(new BigDecimal("100.00")).build(),
                        Strategy.builder().user(user).assetType(fii).targetPercentage(BigDecimal.ZERO).build()));

            assertThat(item(dto, "Ações").amountToRebalance()).isEqualByComparingTo("0.00");
            assertThat(item(dto, "FIIs").amountToRebalance()).isEqualByComparingTo("-1000.00");
        }

        @Test
        @DisplayName("A linha da reserva vem do StrategyReservationService, fora dos itens por tipo")
        void reserveIsOwnRow() {
            RebalanceDTO.ReservationRebalance row = new RebalanceDTO.ReservationRebalance(
                ReservationType.PERCENTAGE, new BigDecimal("30.00"), null, new BigDecimal("3000.00"),
                new BigDecimal("1428.57"), new BigDecimal("4428.57"), new BigDecimal("20.00"),
                new BigDecimal("-10.00"));
            when(strategyReservationService.rebalance(any(), any())).thenReturn(row);

            RebalanceDTO dto = rebalancear(
                portfolioEntity(stock, "5000.00", false),
                List.of(Strategy.builder().user(user).assetType(stock).targetPercentage(new BigDecimal("100.00")).build()));

            assertThat(dto.reservation()).isSameAs(row);
        }
    }
}
