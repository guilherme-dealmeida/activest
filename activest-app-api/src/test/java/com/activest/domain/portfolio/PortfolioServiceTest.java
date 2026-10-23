package com.activest.domain.portfolio;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetCategory;
import com.activest.domain.asset.AssetType;
import com.activest.domain.asset.PriceSource;
import com.activest.domain.dividend.DividendCalendar;
import com.activest.domain.dividend.DividendRepository;
import com.activest.domain.dividend.PositionDividendTotal;
import com.activest.domain.portfolio.dto.PortfolioAllocationDTO;
import com.activest.domain.portfolio.dto.PortfolioAllocationDetailedDTO;
import com.activest.domain.portfolio.dto.PortfolioAssetDetailDTO;
import com.activest.domain.portfolio.dto.PortfolioSummaryDTO;
import com.activest.domain.portfolio.dto.ReservationAllocationDTO;
import com.activest.domain.user.UserRepository;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioServiceTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioAssetRepository portfolioAssetRepository;
    @Mock private UserRepository userRepository;
    @Mock private DividendRepository dividendRepository;
    @Mock private PositionCalculator positionCalculator;
    @Mock private DividendCalendar dividendCalendar;
    @Mock private com.activest.domain.asset.AssetLogos assetLogos;

    @InjectMocks private PortfolioService service;

    /** Proventos so entram depois de recebidos: a soma do banco recebe o hoje da B3. */
    private static final java.time.LocalDate TODAY = java.time.LocalDate.of(2026, 9, 24);

    private UUID userId;
    private Portfolio portfolioEntity;
    private AssetType stockType;
    private AssetType fiiType;
    private AssetType fixedIncomeType;

    private final List<PortfolioAsset> positions = new ArrayList<>();
    private final Map<UUID, Position> calculatedPositions = new HashMap<>();

    @BeforeEach
    void scenario() {
        userId = UUID.randomUUID();
        portfolioEntity = Portfolio.builder().name("Minha Carteira").build();
        portfolioEntity.setId(UUID.randomUUID());

        stockType = typeValue(1L, "ACAO", "Ações", PriceSource.BRAPI, false);
        fiiType = typeValue(2L, "FII", "FIIs", PriceSource.BRAPI, false);
        fixedIncomeType = typeValue(3L, "RENDA_FIXA", "Renda Fixa", PriceSource.MANUAL, true);

        when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.of(portfolioEntity));
        when(portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolioEntity.getId())).thenReturn(positions);
        when(positionCalculator.positionsOf(any(), any())).thenReturn(calculatedPositions);
        when(dividendCalendar.today()).thenReturn(TODAY);
        when(dividendRepository.sumTotalAmountByPosition(portfolioEntity.getId(), TODAY)).thenReturn(List.of());
    }

    private static AssetType typeValue(Long id, String name, String description, PriceSource source, boolean fixedIncome) {
        AssetType type = AssetType.builder()
            .name(name).description(description).priceSource(source).fixedIncome(fixedIncome)
            .colorHex("#c9a84c").build();
        type.setId(id);
        return type;
    }

    /** Uma posicao por cota: custo, valorizacao gravada e, opcionalmente, reserva. */
    private PortfolioAsset position(AssetType typeValue, String ticker, String cost, String appreciationValue,
                                   String shares, boolean reserve) {
        Asset asset = Asset.builder().ticker(ticker).name(ticker).assetType(typeValue)
            .currentPrice(new BigDecimal("10.00")).build();
        PortfolioAsset pa = PortfolioAsset.builder()
            .portfolio(portfolioEntity).asset(asset)
            .appreciation(new BigDecimal(appreciationValue))
            .isReservation(reserve)
            .build();
        pa.setId(UUID.randomUUID());
        positions.add(pa);
        calculatedPositions.put(pa.getId(), new Position(
            shares == null ? null : new BigDecimal(shares),
            shares == null ? null : new BigDecimal("1.0000"),
            new BigDecimal(cost), BigDecimal.ZERO));
        return pa;
    }

    @Nested
    @DisplayName("Resumo e posicao")
    class PortfolioRow {

        @Test
        @DisplayName("Valor atual e custo mais valorizacao; o retorno inclui proventos")
        void summary() {
            PortfolioAsset petr = position(stockType, "PETR4", "1000.00", "200.00", "100", false);
            when(dividendRepository.sumTotalAmountByPosition(portfolioEntity.getId(), TODAY))
                .thenReturn(List.of(new PositionDividendTotal(petr.getId(), new BigDecimal("50.00"))));

            PortfolioSummaryDTO summary = service.getPortfolioSummary(userId);

            assertThat(summary.totalInvested()).isEqualByComparingTo("1000.00");
            assertThat(summary.currentValue()).isEqualByComparingTo("1200.00");
            assertThat(summary.totalDividends()).isEqualByComparingTo("50.00");
            assertThat(summary.totalReturn()).isEqualByComparingTo("250.00");
            assertThat(summary.returnPercentage()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("Carteira vazia responde zeros, sem dividir por zero")
        void emptyPortfolio() {
            PortfolioSummaryDTO summary = service.getPortfolioSummary(userId);

            assertThat(summary.totalInvested()).isEqualByComparingTo("0");
            assertThat(summary.currentValue()).isEqualByComparingTo("0");
            assertThat(summary.returnPercentage()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Posicao liquidada sai dos totais, mas os proventos recebidos continuam contando")
        void settledPosition() {
            PortfolioAsset sold = position(stockType, "PETR4", "0.00", "300.00", "0", false);
            sold.setSettled(true);
            when(dividendRepository.sumTotalAmountByPosition(portfolioEntity.getId(), TODAY))
                .thenReturn(List.of(new PositionDividendTotal(sold.getId(), new BigDecimal("40.00"))));

            PortfolioSummaryDTO summary = service.getPortfolioSummary(userId);

            assertThat(summary.totalInvested()).isEqualByComparingTo("0");
            assertThat(summary.currentValue()).isEqualByComparingTo("0");
            assertThat(summary.totalDividends()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("Posicao sem custo fica fora do resumo, mesmo com valorizacao gravada")
        void positionWithoutCostOutOfSummary() {
            position(fixedIncomeType, "CDB", "0.00", "300.00", null, true);

            PortfolioSummaryDTO summary = service.getPortfolioSummary(userId);

            assertThat(summary.totalInvested()).isEqualByComparingTo("0");
            assertThat(summary.currentValue()).isEqualByComparingTo("0");
            assertThat(summary.totalReturn()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("A listagem traz o tipo pela descricao e o titulo (emissor e vencimento) da renda fixa")
        void assetListing() {
            position(stockType, "PETR4", "1000.00", "200.00", "100", false);

            List<PortfolioAssetDetailDTO> list = service.getPortfolioAssets(userId);

            assertThat(list).hasSize(1);
            PortfolioAssetDetailDTO row = list.get(0);
            assertThat(row.typeName()).isEqualTo("Ações");
            assertThat(row.currentValue()).isEqualByComparingTo("1200.00");
            assertThat(row.valorization()).isEqualByComparingTo("200.00");
            assertThat(row.returnPercentage()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("Posicao liquidada e posicao sem custo ficam fora da listagem")
        void listingOnlyOpenPositions() {
            PortfolioAsset settled = position(stockType, "PETR4", "0.00", "0.00", "0", false);
            settled.setSettled(true);
            position(fiiType, "HGLG11", "0.00", "0.00", "0", false);

            assertThat(service.getPortfolioAssets(userId)).isEmpty();
        }

        @Test
        @DisplayName("Renda fixa sem cotacao: valor atual igual ao aplicado enquanto a valorizacao for zero")
        void fixedIncomeWithoutQuote() {
            position(fixedIncomeType, "CDB", "1000.00", "0.00", null, false);

            PortfolioAssetDetailDTO row = service.getPortfolioAssets(userId).get(0);

            assertThat(row.currentValue()).isEqualByComparingTo("1000.00");
            assertThat(row.valorization()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Sem carteira, uma e criada na primeira leitura")
        void portfolioCreatedOnFirstRead() {
            when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(
                com.activest.domain.user.User.builder().email("dono@activest.test").build()));
            when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(i -> {
                Portfolio p = i.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            Portfolio createdId = service.getOrCreatePortfolio(userId);

            assertThat(createdId).isNotNull();
            verify(portfolioRepository).save(any(Portfolio.class));
        }
    }

    @Nested
    @DisplayName("Reserva de emergencia")
    class Reserve {

        @Test
        @DisplayName("So renda fixa compoe reserva — a regra olha a coluna fixed_income do tipo")
        void onlyFixedIncome() {
            PortfolioAsset stock = position(stockType, "PETR4", "1000.00", "0.00", "100", false);
            when(portfolioAssetRepository.findOwnedById(userId, stock.getId())).thenReturn(Optional.of(stock));

            assertThatThrownBy(() -> service.setReservation(userId, stock.getId(), true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Somente ativos de renda fixa podem compor a reserva de emergência");
        }

        @Test
        @DisplayName("A requisicao manda o ESTADO desejado: reenviar o mesmo valor nao muda nada")
        void idempotente() {
            PortfolioAsset cdb = position(fixedIncomeType, "CDB", "1000.00", "0.00", null, true);
            when(portfolioAssetRepository.findOwnedById(userId, cdb.getId())).thenReturn(Optional.of(cdb));

            service.setReservation(userId, cdb.getId(), true);
            service.setReservation(userId, cdb.getId(), true);

            assertThat(cdb.isReservation()).isTrue();
        }

        @Test
        @DisplayName("Desmarcar tira a posicao da reserva")
        void unmark() {
            PortfolioAsset cdb = position(fixedIncomeType, "CDB", "1000.00", "0.00", null, true);
            when(portfolioAssetRepository.findOwnedById(userId, cdb.getId())).thenReturn(Optional.of(cdb));

            service.setReservation(userId, cdb.getId(), false);

            assertThat(cdb.isReservation()).isFalse();
        }

        @Test
        @DisplayName("Posicao de outro dono responde 'nao encontrada', nunca 'sem permissao'")
        void otherOwnerPosition() {
            UUID id = UUID.randomUUID();
            when(portfolioAssetRepository.findOwnedById(userId, id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setReservation(userId, id, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Posição não encontrada");
        }

        @Test
        @DisplayName("A alocacao da reserva divide a carteira entre reserva e geracao de renda")
        void reserveAllocation() {
            position(fixedIncomeType, "CDB", "2000.00", "0.00", null, true);
            position(stockType, "PETR4", "7000.00", "1000.00", "100", false);

            ReservationAllocationDTO dto = service.getReservationAllocation(userId);

            assertThat(dto.totalValue()).isEqualByComparingTo("10000.00");
            assertThat(dto.reservationValue()).isEqualByComparingTo("2000.00");
            assertThat(dto.reservationPercentage()).isEqualByComparingTo("20.00");
            assertThat(dto.incomeValue()).isEqualByComparingTo("8000.00");
            assertThat(dto.incomePercentage()).isEqualByComparingTo("80.00");
        }

        @Test
        @DisplayName("Carteira vazia nao divide por zero na alocacao da reserva")
        void emptyReserveAllocation() {
            ReservationAllocationDTO dto = service.getReservationAllocation(userId);

            assertThat(dto.totalValue()).isEqualByComparingTo("0");
            assertThat(dto.reservationPercentage()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Alocacao")
    class Allocation {

        @Test
        @DisplayName("Sem excludeReservation (Dashboard) a reserva entra na conta")
        void dashboardIncludesReserve() {
            position(fixedIncomeType, "CDB", "2000.00", "0.00", null, true);
            position(stockType, "PETR4", "8000.00", "0.00", "100", false);

            List<PortfolioAllocationDTO> allocation = service.getAllocation(userId, false);

            assertThat(allocation).extracting(PortfolioAllocationDTO::typeName)
                .containsExactlyInAnyOrder("Ações", "Renda Fixa");
            assertThat(allocation.stream().map(PortfolioAllocationDTO::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Com excludeReservation (tela Alocacao) a reserva fica de fora e o resto e 100%")
        void allocationScreenExcludesReserve() {
            position(fixedIncomeType, "CDB", "2000.00", "0.00", null, true);
            position(stockType, "PETR4", "6000.00", "0.00", "100", false);
            position(fiiType, "HGLG11", "2000.00", "0.00", "50", false);

            List<PortfolioAllocationDTO> allocation = service.getAllocation(userId, true);

            assertThat(allocation).extracting(PortfolioAllocationDTO::typeName)
                .containsExactlyInAnyOrder("Ações", "FIIs");
            assertThat(allocation.stream().filter(a -> a.typeName().equals("Ações")).findFirst().orElseThrow()
                .percentage()).isEqualByComparingTo("75.00");
        }

        @Test
        @DisplayName("O total da alocacao sem reserva bate com a geracao de renda do grafico da reserva")
        void allocationWithoutReserveMatchesIncomeGeneration() {
            position(fixedIncomeType, "CDB", "2000.00", "0.00", null, true);
            position(stockType, "PETR4", "6000.00", "1000.00", "100", false);

            BigDecimal totalWithoutReserve = service.getAllocation(userId, true).stream()
                .map(PortfolioAllocationDTO::value).reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalWithoutReserve).isEqualByComparingTo(service.getReservationAllocation(userId).incomeValue());
        }

        @Test
        @DisplayName("Posicao liquidada e posicao sem custo nao entram na alocacao")
        void allocationOnlyOpenPositions() {
            PortfolioAsset settled = position(stockType, "PETR4", "1000.00", "0.00", "0", false);
            settled.setSettled(true);
            position(fiiType, "HGLG11", "0.00", "0.00", "0", false);

            assertThat(service.getAllocation(userId, false)).isEmpty();
        }

        @Test
        @DisplayName("A alocacao detalhada agrupa por tipo e categoria, e nunca inclui a reserva")
        void detailedAllocation() {
            PortfolioAsset petr = position(stockType, "PETR4", "6000.00", "0.00", "100", false);
            petr.getAsset().setAssetCategory(category("PETROLEO", "Exploração"));
            PortfolioAsset worth = position(stockType, "VALE3", "2000.00", "0.00", "50", false);
            worth.getAsset().setAssetCategory(category("MINERACAO", "Minerais"));
            position(fixedIncomeType, "CDB", "2000.00", "0.00", null, true);

            List<PortfolioAllocationDetailedDTO> detailed = service.getAllocationDetailed(userId);

            assertThat(detailed).hasSize(1);
            PortfolioAllocationDetailedDTO shares = detailed.get(0);
            assertThat(shares.typeName()).isEqualTo("Ações");
            assertThat(shares.totalValue()).isEqualByComparingTo("8000.00");
            assertThat(shares.categories()).hasSize(2);
            assertThat(shares.categories()).extracting(PortfolioAllocationDetailedDTO.CategoryAllocation::categoryName)
                .containsExactlyInAnyOrder("Exploração", "Minerais");
        }

        private AssetCategory category(String displayName, String descriptionText) {
            return AssetCategory.builder().name(displayName).description(descriptionText).build();
        }
    }
}
