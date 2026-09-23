package com.primevest.domain.portfolio;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetCategory;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.dividend.DividendRepository;
import com.primevest.domain.dividend.PositionDividendTotal;
import com.primevest.domain.portfolio.dto.PortfolioAllocationDTO;
import com.primevest.domain.portfolio.dto.PortfolioAllocationDetailedDTO;
import com.primevest.domain.portfolio.dto.PortfolioAssetDetailDTO;
import com.primevest.domain.portfolio.dto.PortfolioSummaryDTO;
import com.primevest.domain.portfolio.dto.ReservationAllocationDTO;
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

    @InjectMocks private PortfolioService service;

    private UUID userId;
    private Portfolio carteira;
    private AssetType acaoTipo;
    private AssetType fiiTipo;
    private AssetType rendaFixaTipo;

    private final List<PortfolioAsset> posicoes = new ArrayList<>();
    private final Map<UUID, Position> posicoesCalculadas = new HashMap<>();

    @BeforeEach
    void cenario() {
        userId = UUID.randomUUID();
        carteira = Portfolio.builder().name("Minha Carteira").build();
        carteira.setId(UUID.randomUUID());

        acaoTipo = tipo(1L, "ACAO", "Ações", PriceSource.BRAPI, false);
        fiiTipo = tipo(2L, "FII", "FIIs", PriceSource.BRAPI, false);
        rendaFixaTipo = tipo(3L, "RENDA_FIXA", "Renda Fixa", PriceSource.MANUAL, true);

        when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.of(carteira));
        when(portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(carteira.getId())).thenReturn(posicoes);
        when(positionCalculator.positionsOf(any(), any())).thenReturn(posicoesCalculadas);
        when(dividendRepository.sumTotalAmountByPosition(carteira.getId())).thenReturn(List.of());
    }

    private static AssetType tipo(Long id, String name, String description, PriceSource source, boolean fixedIncome) {
        AssetType type = AssetType.builder()
            .name(name).description(description).priceSource(source).fixedIncome(fixedIncome)
            .colorHex("#c9a84c").build();
        type.setId(id);
        return type;
    }

    /** Uma posicao por cota: custo, valorizacao gravada e, opcionalmente, reserva. */
    private PortfolioAsset posicao(AssetType tipo, String ticker, String custo, String valorizacao,
                                   String cotas, boolean reserva) {
        Asset asset = Asset.builder().ticker(ticker).name(ticker).assetType(tipo)
            .currentPrice(new BigDecimal("10.00")).build();
        PortfolioAsset pa = PortfolioAsset.builder()
            .portfolio(carteira).asset(asset)
            .appreciation(new BigDecimal(valorizacao))
            .isReservation(reserva)
            .build();
        pa.setId(UUID.randomUUID());
        posicoes.add(pa);
        posicoesCalculadas.put(pa.getId(), new Position(
            cotas == null ? null : new BigDecimal(cotas),
            cotas == null ? null : new BigDecimal("1.0000"),
            new BigDecimal(custo), BigDecimal.ZERO));
        return pa;
    }

    @Nested
    @DisplayName("Resumo e posicao")
    class Carteira {

        @Test
        @DisplayName("Valor atual e custo mais valorizacao; o retorno inclui proventos")
        void resumo() {
            PortfolioAsset petr = posicao(acaoTipo, "PETR4", "1000.00", "200.00", "100", false);
            when(dividendRepository.sumTotalAmountByPosition(carteira.getId()))
                .thenReturn(List.of(new PositionDividendTotal(petr.getId(), new BigDecimal("50.00"))));

            PortfolioSummaryDTO resumo = service.getPortfolioSummary(userId);

            assertThat(resumo.totalInvested()).isEqualByComparingTo("1000.00");
            assertThat(resumo.currentValue()).isEqualByComparingTo("1200.00");
            assertThat(resumo.totalDividends()).isEqualByComparingTo("50.00");
            assertThat(resumo.totalReturn()).isEqualByComparingTo("250.00");
            assertThat(resumo.returnPercentage()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("Carteira vazia responde zeros, sem dividir por zero")
        void carteiraVazia() {
            PortfolioSummaryDTO resumo = service.getPortfolioSummary(userId);

            assertThat(resumo.totalInvested()).isEqualByComparingTo("0");
            assertThat(resumo.currentValue()).isEqualByComparingTo("0");
            assertThat(resumo.returnPercentage()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Posicao liquidada sai dos totais, mas os proventos recebidos continuam contando")
        void posicaoLiquidada() {
            PortfolioAsset vendida = posicao(acaoTipo, "PETR4", "0.00", "300.00", "0", false);
            vendida.setSettled(true);
            when(dividendRepository.sumTotalAmountByPosition(carteira.getId()))
                .thenReturn(List.of(new PositionDividendTotal(vendida.getId(), new BigDecimal("40.00"))));

            PortfolioSummaryDTO resumo = service.getPortfolioSummary(userId);

            assertThat(resumo.totalInvested()).isEqualByComparingTo("0");
            assertThat(resumo.currentValue()).isEqualByComparingTo("0");
            assertThat(resumo.totalDividends()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("Posicao sem custo fica fora do resumo, mesmo com valorizacao gravada")
        void posicaoSemCustoForaDoResumo() {
            posicao(rendaFixaTipo, "CDB", "0.00", "300.00", null, true);

            PortfolioSummaryDTO resumo = service.getPortfolioSummary(userId);

            assertThat(resumo.totalInvested()).isEqualByComparingTo("0");
            assertThat(resumo.currentValue()).isEqualByComparingTo("0");
            assertThat(resumo.totalReturn()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("A listagem traz o tipo pela descricao e o titulo (emissor e vencimento) da renda fixa")
        void listagemDeAtivos() {
            posicao(acaoTipo, "PETR4", "1000.00", "200.00", "100", false);

            List<PortfolioAssetDetailDTO> lista = service.getPortfolioAssets(userId);

            assertThat(lista).hasSize(1);
            PortfolioAssetDetailDTO linha = lista.get(0);
            assertThat(linha.typeName()).isEqualTo("Ações");
            assertThat(linha.currentValue()).isEqualByComparingTo("1200.00");
            assertThat(linha.valorization()).isEqualByComparingTo("200.00");
            assertThat(linha.returnPercentage()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("Posicao liquidada e posicao sem custo ficam fora da listagem")
        void listagemSoPosicaoEmAberto() {
            PortfolioAsset liquidada = posicao(acaoTipo, "PETR4", "0.00", "0.00", "0", false);
            liquidada.setSettled(true);
            posicao(fiiTipo, "HGLG11", "0.00", "0.00", "0", false);

            assertThat(service.getPortfolioAssets(userId)).isEmpty();
        }

        @Test
        @DisplayName("Renda fixa sem cotacao: valor atual igual ao aplicado enquanto a valorizacao for zero")
        void rendaFixaSemCotacao() {
            posicao(rendaFixaTipo, "CDB", "1000.00", "0.00", null, false);

            PortfolioAssetDetailDTO linha = service.getPortfolioAssets(userId).get(0);

            assertThat(linha.currentValue()).isEqualByComparingTo("1000.00");
            assertThat(linha.valorization()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Sem carteira, uma e criada na primeira leitura")
        void carteiraCriadaNaPrimeiraLeitura() {
            when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(
                com.primevest.domain.user.User.builder().email("dono@primevest.test").build()));
            when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(i -> {
                Portfolio p = i.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            Portfolio criada = service.getOrCreatePortfolio(userId);

            assertThat(criada).isNotNull();
            verify(portfolioRepository).save(any(Portfolio.class));
        }
    }

    @Nested
    @DisplayName("Reserva de emergencia")
    class Reserva {

        @Test
        @DisplayName("So renda fixa compoe reserva — a regra olha a coluna fixed_income do tipo")
        void somenteRendaFixa() {
            PortfolioAsset acao = posicao(acaoTipo, "PETR4", "1000.00", "0.00", "100", false);
            when(portfolioAssetRepository.findOwnedById(userId, acao.getId())).thenReturn(Optional.of(acao));

            assertThatThrownBy(() -> service.setReservation(userId, acao.getId(), true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Somente ativos de renda fixa podem compor a reserva de emergência");
        }

        @Test
        @DisplayName("A requisicao manda o ESTADO desejado: reenviar o mesmo valor nao muda nada")
        void idempotente() {
            PortfolioAsset cdb = posicao(rendaFixaTipo, "CDB", "1000.00", "0.00", null, true);
            when(portfolioAssetRepository.findOwnedById(userId, cdb.getId())).thenReturn(Optional.of(cdb));

            service.setReservation(userId, cdb.getId(), true);
            service.setReservation(userId, cdb.getId(), true);

            assertThat(cdb.isReservation()).isTrue();
        }

        @Test
        @DisplayName("Desmarcar tira a posicao da reserva")
        void desmarcar() {
            PortfolioAsset cdb = posicao(rendaFixaTipo, "CDB", "1000.00", "0.00", null, true);
            when(portfolioAssetRepository.findOwnedById(userId, cdb.getId())).thenReturn(Optional.of(cdb));

            service.setReservation(userId, cdb.getId(), false);

            assertThat(cdb.isReservation()).isFalse();
        }

        @Test
        @DisplayName("Posicao de outro dono responde 'nao encontrada', nunca 'sem permissao'")
        void posicaoDeOutroDono() {
            UUID id = UUID.randomUUID();
            when(portfolioAssetRepository.findOwnedById(userId, id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setReservation(userId, id, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Posição não encontrada");
        }

        @Test
        @DisplayName("A alocacao da reserva divide a carteira entre reserva e geracao de renda")
        void alocacaoDaReserva() {
            posicao(rendaFixaTipo, "CDB", "2000.00", "0.00", null, true);
            posicao(acaoTipo, "PETR4", "7000.00", "1000.00", "100", false);

            ReservationAllocationDTO dto = service.getReservationAllocation(userId);

            assertThat(dto.totalValue()).isEqualByComparingTo("10000.00");
            assertThat(dto.reservationValue()).isEqualByComparingTo("2000.00");
            assertThat(dto.reservationPercentage()).isEqualByComparingTo("20.00");
            assertThat(dto.incomeValue()).isEqualByComparingTo("8000.00");
            assertThat(dto.incomePercentage()).isEqualByComparingTo("80.00");
        }

        @Test
        @DisplayName("Carteira vazia nao divide por zero na alocacao da reserva")
        void alocacaoDaReservaVazia() {
            ReservationAllocationDTO dto = service.getReservationAllocation(userId);

            assertThat(dto.totalValue()).isEqualByComparingTo("0");
            assertThat(dto.reservationPercentage()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Alocacao")
    class Alocacao {

        @Test
        @DisplayName("Sem excludeReservation (Dashboard) a reserva entra na conta")
        void dashboardIncluiReserva() {
            posicao(rendaFixaTipo, "CDB", "2000.00", "0.00", null, true);
            posicao(acaoTipo, "PETR4", "8000.00", "0.00", "100", false);

            List<PortfolioAllocationDTO> alocacao = service.getAllocation(userId, false);

            assertThat(alocacao).extracting(PortfolioAllocationDTO::typeName)
                .containsExactlyInAnyOrder("Ações", "Renda Fixa");
            assertThat(alocacao.stream().map(PortfolioAllocationDTO::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Com excludeReservation (tela Alocacao) a reserva fica de fora e o resto e 100%")
        void telaAlocacaoExcluiReserva() {
            posicao(rendaFixaTipo, "CDB", "2000.00", "0.00", null, true);
            posicao(acaoTipo, "PETR4", "6000.00", "0.00", "100", false);
            posicao(fiiTipo, "HGLG11", "2000.00", "0.00", "50", false);

            List<PortfolioAllocationDTO> alocacao = service.getAllocation(userId, true);

            assertThat(alocacao).extracting(PortfolioAllocationDTO::typeName)
                .containsExactlyInAnyOrder("Ações", "FIIs");
            assertThat(alocacao.stream().filter(a -> a.typeName().equals("Ações")).findFirst().orElseThrow()
                .percentage()).isEqualByComparingTo("75.00");
        }

        @Test
        @DisplayName("O total da alocacao sem reserva bate com a geracao de renda do grafico da reserva")
        void alocacaoSemReservaBateComGeracaoDeRenda() {
            posicao(rendaFixaTipo, "CDB", "2000.00", "0.00", null, true);
            posicao(acaoTipo, "PETR4", "6000.00", "1000.00", "100", false);

            BigDecimal totalSemReserva = service.getAllocation(userId, true).stream()
                .map(PortfolioAllocationDTO::value).reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalSemReserva).isEqualByComparingTo(service.getReservationAllocation(userId).incomeValue());
        }

        @Test
        @DisplayName("Posicao liquidada e posicao sem custo nao entram na alocacao")
        void alocacaoSoPosicaoEmAberto() {
            PortfolioAsset liquidada = posicao(acaoTipo, "PETR4", "1000.00", "0.00", "0", false);
            liquidada.setSettled(true);
            posicao(fiiTipo, "HGLG11", "0.00", "0.00", "0", false);

            assertThat(service.getAllocation(userId, false)).isEmpty();
        }

        @Test
        @DisplayName("A alocacao detalhada agrupa por tipo e categoria, e nunca inclui a reserva")
        void alocacaoDetalhada() {
            PortfolioAsset petr = posicao(acaoTipo, "PETR4", "6000.00", "0.00", "100", false);
            petr.getAsset().setAssetCategory(categoria("PETROLEO", "Exploração"));
            PortfolioAsset vale = posicao(acaoTipo, "VALE3", "2000.00", "0.00", "50", false);
            vale.getAsset().setAssetCategory(categoria("MINERACAO", "Minerais"));
            posicao(rendaFixaTipo, "CDB", "2000.00", "0.00", null, true);

            List<PortfolioAllocationDetailedDTO> detalhada = service.getAllocationDetailed(userId);

            assertThat(detalhada).hasSize(1);
            PortfolioAllocationDetailedDTO acoes = detalhada.get(0);
            assertThat(acoes.typeName()).isEqualTo("Ações");
            assertThat(acoes.totalValue()).isEqualByComparingTo("8000.00");
            assertThat(acoes.categories()).hasSize(2);
            assertThat(acoes.categories()).extracting(PortfolioAllocationDetailedDTO.CategoryAllocation::categoryName)
                .containsExactlyInAnyOrder("Exploração", "Minerais");
        }

        private AssetCategory categoria(String nome, String descricao) {
            return AssetCategory.builder().name(nome).description(descricao).build();
        }
    }
}
