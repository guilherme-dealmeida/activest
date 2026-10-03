package com.primevest.domain.transaction;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.AssetTerms;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.dividend.DividendRecalculator;
import com.primevest.domain.dividend.Dividend;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.fixedincome.FixedIncomeNoteRepository;
import com.primevest.domain.fixedincome.FixedIncomeSettlement;
import com.primevest.domain.portfolio.AppreciationUpdater;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.portfolio.PositionGuard;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decisao e roteamento do TransactionService — o que ele recusa, o que ele
 * grava e o que ele delega. O efeito combinado (FIFO, liquidacao, valorizacao)
 * fica nos testes funcionais, onde o banco participa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioAssetRepository portfolioAssetRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private FixedIncomeNoteRepository fixedIncomeNoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private DividendRecalculator dividendRecalculator;
    @Mock private PositionCalculator positionCalculator;
    @Mock private AppreciationUpdater appreciationUpdater;
    @Mock private FixedIncomeSettlement fixedIncomeSettlement;
    @Mock private TreasuryAllocator treasuryAllocator;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.primevest.domain.integration.IntegrationCryptoExclusionRepository cryptoExclusionRepository;
    @Mock private com.primevest.domain.integration.source.MovementJson movementJson;

    /** O guard entra de verdade: as regras A e B sao dele, e ja tem teste proprio. */
    private final PositionGuard positionGuard = new PositionGuard();

    private TransactionService service;

    private UUID userId;
    private Portfolio carteira;
    private Asset acao;
    private Asset cdb;

    @BeforeEach
    void cenario() {
        service = new TransactionService(transactionRepository, portfolioRepository, portfolioAssetRepository,
            assetRepository, fixedIncomeNoteRepository, userRepository, dividendRecalculator, positionCalculator,
            appreciationUpdater, positionGuard, fixedIncomeSettlement, treasuryAllocator, eventPublisher,
            cryptoExclusionRepository, movementJson,
            new com.primevest.domain.integration.pipeline.ImportRulesRegistry(java.util.List.of()));

        userId = UUID.randomUUID();
        carteira = Portfolio.builder().name("Minha Carteira").build();
        carteira.setId(UUID.randomUUID());

        acao = asset("PETR4", tipo("ACAO", "Ações", PriceSource.BRAPI, false));
        cdb = asset("CDB", tipo("RENDA_FIXA", "Renda Fixa", PriceSource.MANUAL, true));

        when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.of(carteira));
        when(assetRepository.findByTicker("PETR4")).thenReturn(Optional.of(acao));
        when(assetRepository.findByTicker("CDB")).thenReturn(Optional.of(cdb));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            return tx;
        });
        when(portfolioAssetRepository.save(any(PortfolioAsset.class))).thenAnswer(i -> {
            PortfolioAsset pa = i.getArgument(0);
            if (pa.getId() == null) {
                pa.setId(UUID.randomUUID());
            }
            return pa;
        });
        when(positionCalculator.chronologicalTransactions(any())).thenReturn(List.of());
        when(positionCalculator.calculate(anyBoolean(), anyList()))
            .thenReturn(new com.primevest.domain.portfolio.Position(
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO));
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private static AssetType tipo(String name, String description, PriceSource source, boolean fixedIncome) {
        AssetType type = AssetType.builder()
            .name(name).description(description).priceSource(source).fixedIncome(fixedIncome).build();
        type.setId(1L);
        return type;
    }

    private static Asset asset(String ticker, AssetType tipo) {
        Asset asset = Asset.builder().ticker(ticker).name(ticker).assetType(tipo).build();
        asset.setId(ticker.hashCode() & 0xffL);
        return asset;
    }

    private PortfolioAsset posicaoExistente(Asset asset, FixedIncomeNote note) {
        PortfolioAsset pa = PortfolioAsset.builder()
            .portfolio(carteira).asset(asset).note(note).appreciation(BigDecimal.ZERO).build();
        pa.setId(UUID.randomUUID());
        if (note == null) {
            when(portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(carteira.getId(), asset.getId()))
                .thenReturn(Optional.of(pa));
        } else {
            when(portfolioAssetRepository.findByPortfolioIdAndNoteId(carteira.getId(), note.getId()))
                .thenReturn(Optional.of(pa));
        }
        return pa;
    }

    private static TransactionCreateDTO compraDeAcao(String cotas, String preco) {
        return new TransactionCreateDTO("PETR4", TransactionType.BUY, new BigDecimal(cotas),
            new BigDecimal(preco), null, null, LocalDate.of(2026, 9, 1), null, null,
            null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("Catalogo e carteira")
    class Precondicoes {

        @Test
        @DisplayName("Ticker fora do catalogo e erro: a transacao nao cria ativo")
        void tickerInexistente() {
            when(assetRepository.findByTicker("XXXX9")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("XXXX9", TransactionType.BUY, BigDecimal.ONE, BigDecimal.TEN, null,
                    null, LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ativo não encontrado no catálogo: XXXX9")
                .hasMessageContaining("Selecione um ativo existente na busca");

            verify(assetRepository, never()).save(any());
        }

        @Test
        @DisplayName("Usuario sem carteira recebe mensagem propria")
        void semCarteira() {
            when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTransaction(userId, compraDeAcao("10", "30.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Carteira não encontrada");
        }

        @Test
        @DisplayName("Transacao de outro dono responde 'nao encontrada' — 404, nunca 403")
        void transacaoDeOutroDono() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findOwnedById(userId, txId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTransactionById(userId, txId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transação não encontrada");
            assertThatThrownBy(() -> service.deleteTransaction(userId, txId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Como a transacao e valorada")
    class Valores {

        @Test
        @DisplayName("Renda variavel: o total e calculado, e o que veio no DTO e ignorado")
        void totalCalculado() {
            posicaoExistente(acao, null);
            TransactionCreateDTO comTotalErrado = new TransactionCreateDTO("PETR4", TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("30.00"), new BigDecimal("999999.00"), null,
                LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null);

            service.createTransaction(userId, comTotalErrado);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("Renda variavel sem quantidade ou sem preco e recusada")
        void faltaQuantidadeOuPreco() {
            posicaoExistente(acao, null);

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.BUY, null, new BigDecimal("30.00"), null,
                    null, LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe a quantidade e o preço unitário.");
        }

        @Test
        @DisplayName("Renda fixa com quantidade ou preco unitario e recusada, em vez de descartada em silencio")
        void rendaFixaComQuantidade() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, new BigDecimal("1"), new BigDecimal("1000.00"),
                    null, null, LocalDate.of(2026, 9, 1), null, null, null, "BTG", LocalDate.of(2030, 1, 15),
                    RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Renda Fixa é lançada pelo valor total, sem quantidade nem preço unitário.");
        }

        @Test
        @DisplayName("Renda fixa sem valor total e recusada")
        void rendaFixaSemValor() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, null, null,
                    LocalDate.of(2026, 9, 1), null, null, null, "BTG", LocalDate.of(2030, 1, 15),
                    RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe o valor da transação.");
        }

        @Test
        @DisplayName("A taxa de corretagem ausente vira zero, e nao nulo")
        void corretagemAusente() {
            posicaoExistente(acao, null);

            service.createTransaction(userId, compraDeAcao("10", "30.00"));

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getBrokerageFee()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Regra A — a venda nao abre posicao")
    class RegraA {

        @Test
        @DisplayName("Venda de ativo que nao esta na carteira e recusada")
        void vendaSemPosicao() {
            when(portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(carteira.getId(), acao.getId()))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.SELL, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, null,
                    null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não há posição de PETR4 na carteira para vender.");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Venda em posicao que existe mas nunca teve compra tambem e recusada")
        void vendaEmPosicaoSemCompra() {
            posicaoExistente(acao, null);
            when(positionCalculator.chronologicalTransactions(any())).thenReturn(List.of());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.SELL, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, null,
                    null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não há posição de PETR4 na carteira para vender.");
        }

        @Test
        @DisplayName("Compra abre a posicao normalmente")
        void compraAbrePosicao() {
            when(portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(carteira.getId(), acao.getId()))
                .thenReturn(Optional.empty());

            service.createTransaction(userId, compraDeAcao("10", "30.00"));

            // A posicao e gravada duas vezes: ao nascer e na reavaliacao que
            // sempre roda depois da escrita.
            verify(portfolioAssetRepository, org.mockito.Mockito.atLeastOnce()).save(any(PortfolioAsset.class));
            verify(transactionRepository).save(any(Transaction.class));
        }
    }

    @Nested
    @DisplayName("Liquidacao total e titulo de renda fixa")
    class RendaFixa {

        private FixedIncomeNote nota() {
            FixedIncomeNote note = FixedIncomeNote.builder()
                .asset(cdb).issuer("BTG").maturityDate(LocalDate.of(2030, 1, 15))
                .rateType(RateType.POS_FIXADO).indexer(Indexer.CDI).indexerRate(new BigDecimal("110.0000"))
                .build();
            note.setId(UUID.randomUUID());
            return note;
        }

        @Test
        @DisplayName("Liquidacao total fora de venda de renda fixa e recusada")
        void liquidacaoTotalSoEmVendaDeRendaFixa() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.BUY, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, true,
                    null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Liquidação total só existe em venda de Renda Fixa.");
        }

        @Test
        @DisplayName("Titulo informado para ativo que nao e de renda fixa e recusado")
        void noteIdEmAtivoQueNaoERendaFixa() {
            posicaoExistente(acao, null);

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.BUY, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, null,
                    UUID.randomUUID(), null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Título informado para um ativo que não é de renda fixa: PETR4");
        }

        @Test
        @DisplayName("Titulo de outro usuario responde 'Titulo nao encontrado' — o id vem do cliente")
        void noteIdDeOutroUsuario() {
            UUID noteId = UUID.randomUUID();
            when(fixedIncomeNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, noteId, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Título não encontrado");
        }

        @Test
        @DisplayName("Titulo de outro ativo e recusado")
        void noteIdDeOutroAtivo() {
            FixedIncomeNote deOutroAtivo = nota();
            deOutroAtivo.setAsset(acao);
            when(fixedIncomeNoteRepository.findByIdAndUserId(deOutroAtivo.getId(), userId))
                .thenReturn(Optional.of(deOutroAtivo));

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, deOutroAtivo.getId(), null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("O título selecionado pertence a outro ativo: PETR4");
        }

        @Test
        @DisplayName("Venda de renda fixa sem titulo e recusada: nao se cria titulo numa venda")
        void vendaSemNoteId() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.SELL, null, null, new BigDecimal("500.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Venda de renda fixa precisa apontar qual título está sendo vendido.");
        }

        @Test
        @DisplayName("Compra sem titulo cria o titulo com o que o formulario mandou")
        void compraCriaTitulo() {
            when(fixedIncomeNoteRepository.save(any(FixedIncomeNote.class))).thenAnswer(i -> {
                FixedIncomeNote n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            service.createTransaction(userId, new TransactionCreateDTO("CDB", TransactionType.BUY, null, null,
                new BigDecimal("1000.00"), null, LocalDate.of(2026, 9, 1), null, null, null,
                "BTG", LocalDate.of(2030, 1, 15), RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000")));

            ArgumentCaptor<FixedIncomeNote> captor = ArgumentCaptor.forClass(FixedIncomeNote.class);
            verify(fixedIncomeNoteRepository).save(captor.capture());
            FixedIncomeNote criada = captor.getValue();
            assertThat(criada.getIssuer()).isEqualTo("BTG");
            assertThat(criada.getIndexerRate()).isEqualByComparingTo("110.0000");
            assertThat(criada.getMaturityDate()).isEqualTo(LocalDate.of(2030, 1, 15));
        }

        @Test
        @DisplayName("O que o formulario nao manda e herdado de asset_terms — e copiado, nao referenciado")
        void herancaDeAssetTerms() {
            AssetTerms terms = AssetTerms.builder()
                .maturityDate(LocalDate.of(2050, 8, 15))
                .rateType(RateType.HIBRIDO)
                .indexer(Indexer.IPCA)
                .build();
            cdb.setTerms(terms);
            when(fixedIncomeNoteRepository.save(any(FixedIncomeNote.class))).thenAnswer(i -> {
                FixedIncomeNote n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            service.createTransaction(userId, new TransactionCreateDTO("CDB", TransactionType.BUY, null, null,
                new BigDecimal("1000.00"), null, LocalDate.of(2026, 9, 1), null, null, null,
                "Tesouro Nacional", null, null, null, new BigDecimal("7.0900")));

            ArgumentCaptor<FixedIncomeNote> captor = ArgumentCaptor.forClass(FixedIncomeNote.class);
            verify(fixedIncomeNoteRepository).save(captor.capture());
            assertThat(captor.getValue().getMaturityDate()).isEqualTo(LocalDate.of(2050, 8, 15));
            assertThat(captor.getValue().getIndexer()).isEqualTo(Indexer.IPCA);
        }

        @Test
        @DisplayName("Prefixado com indice limpa o indice, em vez de deixar o banco recusar a linha")
        void prefixadoLimpaIndice() {
            when(fixedIncomeNoteRepository.save(any(FixedIncomeNote.class))).thenAnswer(i -> {
                FixedIncomeNote n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            service.createTransaction(userId, new TransactionCreateDTO("CDB", TransactionType.BUY, null, null,
                new BigDecimal("1000.00"), null, LocalDate.of(2026, 9, 1), null, null, null,
                "BTG", LocalDate.of(2030, 1, 15), RateType.PREFIXADO, Indexer.CDI, new BigDecimal("12.5000")));

            ArgumentCaptor<FixedIncomeNote> captor = ArgumentCaptor.forClass(FixedIncomeNote.class);
            verify(fixedIncomeNoteRepository).save(captor.capture());
            assertThat(captor.getValue().getIndexer()).isNull();
        }

        @Test
        @DisplayName("Pos-fixado sem indice e recusado, com o tipo por extenso na mensagem")
        void posFixadoSemIndice() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, null, "BTG", LocalDate.of(2030, 1, 15),
                    RateType.POS_FIXADO, null, new BigDecimal("110.0000"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Remuneração pós-fixada precisa de um índice (CDI, IPCA, ...).");
        }

        @Test
        @DisplayName("Indice sem forma de remuneracao e recusado")
        void indiceSemForma() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, null, "BTG", LocalDate.of(2030, 1, 15),
                    null, Indexer.CDI, new BigDecimal("110.0000"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe como o título remunera antes de escolher o índice.");
        }

        @Test
        @DisplayName("Editar so quantidade e valor nao apaga emissor, vencimento e taxa do titulo")
        void edicaoSemDadosDeTituloNaoApagaNada() {
            FixedIncomeNote note = nota();
            PortfolioAsset pa = posicaoExistente(cdb, note);
            Transaction existente = Transaction.builder()
                .portfolioAsset(pa).note(note).type(TransactionType.BUY)
                .totalAmount(new BigDecimal("1000.00")).transactionDate(LocalDate.of(2026, 9, 1))
                .build();
            existente.setId(UUID.randomUUID());
            when(transactionRepository.findOwnedById(userId, existente.getId())).thenReturn(Optional.of(existente));

            service.updateTransaction(userId, existente.getId(), new TransactionCreateDTO("CDB",
                TransactionType.BUY, null, null, new BigDecimal("1500.00"), null,
                LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null));

            assertThat(note.getIssuer()).isEqualTo("BTG");
            assertThat(note.getMaturityDate()).isEqualTo(LocalDate.of(2030, 1, 15));
            assertThat(note.getIndexerRate()).isEqualByComparingTo("110.0000");
            verify(fixedIncomeNoteRepository, never()).save(any(FixedIncomeNote.class));
        }

        @Test
        @DisplayName("Editar mandando dados de titulo atualiza o titulo")
        void edicaoComDadosDeTituloAtualiza() {
            FixedIncomeNote note = nota();
            PortfolioAsset pa = posicaoExistente(cdb, note);
            Transaction existente = Transaction.builder()
                .portfolioAsset(pa).note(note).type(TransactionType.BUY)
                .totalAmount(new BigDecimal("1000.00")).transactionDate(LocalDate.of(2026, 9, 1))
                .build();
            existente.setId(UUID.randomUUID());
            when(transactionRepository.findOwnedById(userId, existente.getId())).thenReturn(Optional.of(existente));

            service.updateTransaction(userId, existente.getId(), new TransactionCreateDTO("CDB",
                TransactionType.BUY, null, null, new BigDecimal("1000.00"), null,
                LocalDate.of(2026, 9, 1), null, null, null, "Inter", LocalDate.of(2031, 5, 10),
                RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("105.0000")));

            assertThat(note.getIssuer()).isEqualTo("Inter");
            assertThat(note.getIndexerRate()).isEqualByComparingTo("105.0000");
            verify(fixedIncomeNoteRepository).save(note);
        }
    }

    @Nested
    @DisplayName("Regra K — posicao sem transacao deixa de existir")
    class RegraK {

        private Transaction transacaoDe(PortfolioAsset pa, TransactionType tipo) {
            Transaction tx = Transaction.builder()
                .portfolioAsset(pa).type(tipo)
                .shares(BigDecimal.TEN).pricePerShare(BigDecimal.TEN).totalAmount(new BigDecimal("100.00"))
                .transactionDate(LocalDate.of(2026, 9, 1))
                .build();
            tx.setId(UUID.randomUUID());
            when(transactionRepository.findOwnedById(userId, tx.getId())).thenReturn(Optional.of(tx));
            return tx;
        }

        private FixedIncomeNote titulo(Asset asset) {
            FixedIncomeNote note = FixedIncomeNote.builder().asset(asset).issuer("BTG").build();
            note.setId(UUID.randomUUID());
            return note;
        }

        private void semTransacoesRestantes(PortfolioAsset pa) {
            when(transactionRepository.findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId()))
                .thenReturn(List.of());
        }

        @Test
        @DisplayName("Regra K — excluir a ultima transacao de uma acao apaga a posicao, sem reavalia-la")
        void regraK_acao() {
            PortfolioAsset pa = posicaoExistente(acao, null);
            Transaction tx = transacaoDe(pa, TransactionType.BUY);
            semTransacoesRestantes(pa);

            service.deleteTransaction(userId, tx.getId());

            verify(transactionRepository).delete(tx);
            verify(portfolioAssetRepository).delete(pa);
            verify(fixedIncomeNoteRepository, never()).delete(any());
            verify(appreciationUpdater, never()).refresh(any());
        }

        @Test
        @DisplayName("Regra K — na renda fixa a posicao sai com o titulo, e o titulo depois dela (FK)")
        void regraK_rendaFixa() {
            FixedIncomeNote note = titulo(cdb);
            PortfolioAsset pa = posicaoExistente(cdb, note);
            Transaction tx = transacaoDe(pa, TransactionType.BUY);
            semTransacoesRestantes(pa);

            service.deleteTransaction(userId, tx.getId());

            InOrder ordem = inOrder(portfolioAssetRepository, fixedIncomeNoteRepository);
            ordem.verify(portfolioAssetRepository).delete(pa);
            ordem.verify(fixedIncomeNoteRepository).delete(note);
            verify(fixedIncomeSettlement, never()).recompute(any(), anyList(), anyBoolean());
        }

        @Test
        @DisplayName("Regra K — com transacao restante a posicao continua e e reavaliada")
        void regraK_restamTransacoes() {
            PortfolioAsset pa = posicaoExistente(acao, null);
            Transaction tx = transacaoDe(pa, TransactionType.BUY);
            Transaction outra = Transaction.builder().portfolioAsset(pa).type(TransactionType.BUY).build();
            when(transactionRepository.findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId()))
                .thenReturn(List.of(outra));

            service.deleteTransaction(userId, tx.getId());

            verify(portfolioAssetRepository, never()).delete(any());
            verify(appreciationUpdater).refresh(pa);
        }

        @Test
        @DisplayName("Regra K — no Tesouro o titulo sai mesmo tendo provento registrado")
        void regraK_tesouroComProvento() {
            Asset tesouro = asset("TESOURO", tipo("TESOURO_DIRETO", "Tesouro Direto", PriceSource.MANUAL, true));
            FixedIncomeNote note = titulo(tesouro);
            PortfolioAsset pa = posicaoExistente(tesouro, note);
            pa.getDividends().add(Dividend.builder()
                .portfolioAsset(pa).amountPerShare(BigDecimal.ONE).exDate(LocalDate.of(2026, 9, 5)).build());
            Transaction tx = transacaoDe(pa, TransactionType.BUY);
            semTransacoesRestantes(pa);
            when(transactionRepository.findByPortfolioIdAndAssetId(carteira.getId(), tesouro.getId()))
                .thenReturn(List.of());
            when(treasuryAllocator.salesOf(anyList())).thenReturn(List.of());
            when(portfolioAssetRepository.findAllByPortfolioIdAndAssetId(carteira.getId(), tesouro.getId()))
                .thenReturn(List.of());

            service.deleteTransaction(userId, tx.getId());

            verify(portfolioAssetRepository).delete(pa);
            verify(fixedIncomeNoteRepository).delete(note);
        }
    }
}
