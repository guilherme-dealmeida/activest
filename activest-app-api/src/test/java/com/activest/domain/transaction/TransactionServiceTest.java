package com.activest.domain.transaction;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.AssetTerms;
import com.activest.domain.asset.AssetType;
import com.activest.domain.asset.Indexer;
import com.activest.domain.asset.PriceSource;
import com.activest.domain.asset.RateType;
import com.activest.domain.dividend.DividendRecalculator;
import com.activest.domain.dividend.Dividend;
import com.activest.domain.fixedincome.FixedIncomeNote;
import com.activest.domain.fixedincome.FixedIncomeNoteRepository;
import com.activest.domain.fixedincome.FixedIncomeSettlement;
import com.activest.domain.portfolio.AppreciationUpdater;
import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioAssetRepository;
import com.activest.domain.portfolio.PortfolioRepository;
import com.activest.domain.portfolio.PositionCalculator;
import com.activest.domain.portfolio.PositionGuard;
import com.activest.domain.transaction.dto.TransactionCreateDTO;
import com.activest.domain.user.UserRepository;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
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
    @Mock private com.activest.domain.fixedincome.FixedIncomeValuation fixedIncomeValuation;
    @Mock private TreasuryAllocator treasuryAllocator;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.activest.domain.integration.IntegrationCryptoExclusionRepository cryptoExclusionRepository;
    @Mock private com.activest.domain.integration.source.MovementJson movementJson;
    @Mock private com.activest.domain.asset.AssetLogos assetLogos;
    @Mock private com.activest.domain.fixedincome.FixedIncomeIssuers fixedIncomeIssuers;
    @Mock private com.activest.domain.notification.NotificationService notifications;

    /** Empresas de renda fixa do teste, pelo nome (PLANO_EMISSORAS_RENDA_FIXA). */
    private final java.util.Map<String, com.activest.domain.fixedincome.FixedIncomeIssuer> companies =
        new java.util.HashMap<>();
    private static final Long DISTRIBUTOR = 900L;

    private com.activest.domain.fixedincome.FixedIncomeIssuer company(String displayName) {
        return companies.computeIfAbsent(displayName, n -> {
            var i = com.activest.domain.fixedincome.FixedIncomeIssuer.builder()
                .kind(com.activest.domain.fixedincome.FixedIncomeIssuerKind.BANCO).legalName(n).build();
            i.setId(100L + companies.size());
            return i;
        });
    }

    private Long issuerEntity(String displayName) {
        return company(displayName).getId();
    }

    /** O guard entra de verdade: as regras A e B sao dele, e ja tem teste proprio. */
    private final PositionGuard positionGuard = new PositionGuard();

    private TransactionService service;

    private UUID userId;
    private Portfolio portfolioEntity;
    private Asset stock;
    private Asset cdb;

    @BeforeEach
    void scenario() {
        service = new TransactionService(transactionRepository, portfolioRepository, portfolioAssetRepository,
            new com.activest.domain.asset.AssetResolver(assetRepository), fixedIncomeNoteRepository, userRepository, dividendRecalculator, positionCalculator,
            appreciationUpdater, positionGuard, fixedIncomeSettlement, fixedIncomeValuation, treasuryAllocator, eventPublisher,
            cryptoExclusionRepository, movementJson,
            new com.activest.domain.integration.pipeline.ImportRulesRegistry(java.util.List.of()), assetLogos,
            fixedIncomeIssuers, notifications);
        var distributorEntity = com.activest.domain.fixedincome.FixedIncomeIssuer.builder()
            .kind(com.activest.domain.fixedincome.FixedIncomeIssuerKind.CORRETORA_DISTRIBUIDORA)
            .legalName("Corretora").build();
        distributorEntity.setId(DISTRIBUTOR);
        when(fixedIncomeIssuers.distributor(DISTRIBUTOR)).thenReturn(distributorEntity);
        when(fixedIncomeIssuers.issuerFor(any(), any())).thenAnswer(i -> companies.values().stream()
            .filter(e -> e.getId().equals(i.getArgument(1))).findFirst().orElse(null));
        when(fixedIncomeIssuers.info(any()))
            .thenReturn(com.activest.domain.fixedincome.FixedIncomeIssuers.NoteInfo.NONE);

        userId = UUID.randomUUID();
        portfolioEntity = Portfolio.builder().name("Minha Carteira").build();
        portfolioEntity.setId(UUID.randomUUID());

        stock = asset("PETR4", typeValue("ACAO", "Ações", PriceSource.BRAPI, false));
        cdb = asset("CDB", typeValue("RENDA_FIXA", "Renda Fixa", PriceSource.MANUAL, true));

        when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.of(portfolioEntity));
        // O resolver real sobre o repositorio simulado: sem o tipo, vale o ticker que nao se repete (V13)
        when(assetRepository.findAllByTicker("PETR4")).thenReturn(List.of(stock));
        when(assetRepository.findAllByTicker("CDB")).thenReturn(List.of(cdb));
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
            .thenReturn(new com.activest.domain.portfolio.Position(
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO));
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private static AssetType typeValue(String name, String description, PriceSource source, boolean fixedIncome) {
        AssetType type = AssetType.builder()
            .name(name).description(description).priceSource(source).fixedIncome(fixedIncome).build();
        type.setId(1L);
        return type;
    }

    private static Asset asset(String ticker, AssetType typeValue) {
        Asset asset = Asset.builder().ticker(ticker).name(ticker).assetType(typeValue).build();
        asset.setId(ticker.hashCode() & 0xffL);
        return asset;
    }

    private PortfolioAsset existingPosition(Asset asset, FixedIncomeNote note) {
        PortfolioAsset pa = PortfolioAsset.builder()
            .portfolio(portfolioEntity).asset(asset).note(note).appreciation(BigDecimal.ZERO).build();
        pa.setId(UUID.randomUUID());
        if (note == null) {
            when(portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(portfolioEntity.getId(), asset.getId()))
                .thenReturn(Optional.of(pa));
        } else {
            when(portfolioAssetRepository.findByPortfolioIdAndNoteId(portfolioEntity.getId(), note.getId()))
                .thenReturn(Optional.of(pa));
        }
        return pa;
    }

    private static TransactionCreateDTO stockBuy(String shareCount, String price) {
        return new TransactionCreateDTO("PETR4", TransactionType.BUY, new BigDecimal(shareCount),
            new BigDecimal(price), null, null, LocalDate.of(2026, 9, 1), null, null,
            null, null, null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("Catalogo e carteira")
    class Preconditions {

        @Test
        @DisplayName("Ticker fora do catalogo e erro: a transacao nao cria ativo")
        void missingTicker() {
            when(assetRepository.findAllByTicker("XXXX9")).thenReturn(List.of());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("XXXX9", TransactionType.BUY, BigDecimal.ONE, BigDecimal.TEN, null,
                    null, LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ativo não encontrado no catálogo: XXXX9")
                .hasMessageContaining("Selecione um ativo existente na busca");

            verify(assetRepository, never()).save(any());
        }

        @Test
        @DisplayName("D11: papel que saiu da bolsa recusa transacao depois da saida; ate a saida, aceita")
        void afterDelisting() {
            stock.setActive(false);
            stock.setDelistedOn(LocalDate.of(2026, 8, 31));

            assertThatThrownBy(() -> service.createTransaction(userId, stockBuy("10", "30.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PETR4 saiu da bolsa em 31/08/2026");
            TransactionService.requireListedOn(stock, LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("Usuario sem carteira recebe mensagem propria")
        void withoutPortfolio() {
            when(portfolioRepository.findFirstByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTransaction(userId, stockBuy("10", "30.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Carteira não encontrada");
        }

        @Test
        @DisplayName("Transacao de outro dono responde 'nao encontrada' — 404, nunca 403")
        void otherOwnerTransaction() {
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
    class Amounts {

        @Test
        @DisplayName("Renda variavel: o total e calculado, e o que veio no DTO e ignorado")
        void calculatedTotal() {
            existingPosition(stock, null);
            TransactionCreateDTO withWrongTotal = new TransactionCreateDTO("PETR4", TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("30.00"), new BigDecimal("999999.00"), null,
                LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null, null, null);

            service.createTransaction(userId, withWrongTotal);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("Renda variavel sem quantidade ou sem preco e recusada")
        void missingQuantityOrPrice() {
            existingPosition(stock, null);

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.BUY, null, new BigDecimal("30.00"), null,
                    null, LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe a quantidade e o preço unitário.");
        }

        @Test
        @DisplayName("Renda fixa com quantidade ou preco unitario e recusada, em vez de descartada em silencio")
        void fixedIncomeWithQuantity() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, new BigDecimal("1"), new BigDecimal("1000.00"),
                    null, null, LocalDate.of(2026, 9, 1), null, null, null, issuerEntity("BTG"), LocalDate.of(2030, 1, 15),
                    RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000"), DISTRIBUTOR, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Renda Fixa é lançada pelo valor total, sem quantidade nem preço unitário.");
        }

        @Test
        @DisplayName("Renda fixa sem valor total e recusada")
        void fixedIncomeWithoutValue() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, null, null,
                    LocalDate.of(2026, 9, 1), null, null, null, issuerEntity("BTG"), LocalDate.of(2030, 1, 15),
                    RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000"), DISTRIBUTOR, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe o valor da transação.");
        }

        @Test
        @DisplayName("A taxa de corretagem ausente vira zero, e nao nulo")
        void missingBrokerage() {
            existingPosition(stock, null);

            service.createTransaction(userId, stockBuy("10", "30.00"));

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getBrokerageFee()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Regra A — a venda nao abre posicao")
    class RuleA {

        @Test
        @DisplayName("Venda de ativo que nao esta na carteira e recusada")
        void saleWithoutPosition() {
            when(portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(portfolioEntity.getId(), stock.getId()))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.SELL, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, null,
                    null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não há posição de PETR4 na carteira para vender.");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Venda em posicao que existe mas nunca teve compra tambem e recusada")
        void saleOnPositionWithoutBuy() {
            existingPosition(stock, null);
            when(positionCalculator.chronologicalTransactions(any())).thenReturn(List.of());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.SELL, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, null,
                    null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não há posição de PETR4 na carteira para vender.");
        }

        @Test
        @DisplayName("Compra abre a posicao normalmente")
        void buyOpensPosition() {
            when(portfolioAssetRepository.findByPortfolioIdAndAssetIdAndNoteIsNull(portfolioEntity.getId(), stock.getId()))
                .thenReturn(Optional.empty());

            service.createTransaction(userId, stockBuy("10", "30.00"));

            // A posicao e gravada duas vezes: ao nascer e na reavaliacao que
            // sempre roda depois da escrita.
            verify(portfolioAssetRepository, org.mockito.Mockito.atLeastOnce()).save(any(PortfolioAsset.class));
            verify(transactionRepository).save(any(Transaction.class));
        }
    }

    @Nested
    @DisplayName("Liquidacao total e titulo de renda fixa")
    class FixedIncome {

        private FixedIncomeNote noteEntity() {
            FixedIncomeNote note = FixedIncomeNote.builder()
                .asset(cdb).issuer(company("BTG")).maturityDate(LocalDate.of(2030, 1, 15))
                .rateType(RateType.POS_FIXADO).indexer(Indexer.CDI).indexerRate(new BigDecimal("110.0000"))
                .build();
            note.setId(UUID.randomUUID());
            return note;
        }

        @Test
        @DisplayName("Liquidacao total fora de venda de renda fixa e recusada")
        void fullSettlementOnlyOnFixedIncomeSale() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.BUY, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, true,
                    null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Liquidação total só existe em venda de Renda Fixa.");
        }

        @Test
        @DisplayName("Titulo informado para ativo que nao e de renda fixa e recusado")
        void noteIdOnNonFixedIncomeAsset() {
            existingPosition(stock, null);

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("PETR4", TransactionType.BUY, new BigDecimal("10"),
                    new BigDecimal("30.00"), null, null, LocalDate.of(2026, 9, 1), null, null,
                    UUID.randomUUID(), null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Título informado para um ativo que não é de renda fixa: PETR4");
        }

        @Test
        @DisplayName("Titulo de outro usuario responde 'Titulo nao encontrado' — o id vem do cliente")
        void noteIdFromOtherUser() {
            UUID noteId = UUID.randomUUID();
            when(fixedIncomeNoteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, noteId, null, null, null, null, null, DISTRIBUTOR, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Título não encontrado");
        }

        @Test
        @DisplayName("Titulo de outro ativo e recusado")
        void noteIdFromOtherAsset() {
            FixedIncomeNote fromOtherAsset = noteEntity();
            fromOtherAsset.setAsset(stock);
            when(fixedIncomeNoteRepository.findByIdAndUserId(fromOtherAsset.getId(), userId))
                .thenReturn(Optional.of(fromOtherAsset));

            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, fromOtherAsset.getId(), null, null, null, null, null, DISTRIBUTOR, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("O título selecionado pertence a outro ativo: PETR4");
        }

        @Test
        @DisplayName("Compra num titulo existente no dia do vencimento ou depois e recusada")
        void buyOnNoteMaturedOnDate() {
            FixedIncomeNote noteEntity = noteEntity();
            when(fixedIncomeNoteRepository.findByIdAndUserId(noteEntity.getId(), userId)).thenReturn(Optional.of(noteEntity));

            for (LocalDate data : List.of(LocalDate.of(2030, 1, 15), LocalDate.of(2030, 2, 1))) {
                assertThatThrownBy(() -> service.createTransaction(userId,
                    new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                        null, data, null, null, noteEntity.getId(), null, null, null, null, null, DISTRIBUTOR, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Título vencido para a data de transação escolhida");
            }
        }

        @Test
        @DisplayName("Venda de renda fixa sem titulo e recusada: nao se cria titulo numa venda")
        void saleWithoutNoteId() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.SELL, null, null, new BigDecimal("500.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null, DISTRIBUTOR, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Venda de renda fixa precisa apontar qual título está sendo vendido.");
        }

        @Test
        @DisplayName("Compra sem titulo cria o titulo com o que o formulario mandou")
        void buyCreatesNote() {
            when(fixedIncomeNoteRepository.save(any(FixedIncomeNote.class))).thenAnswer(i -> {
                FixedIncomeNote n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            service.createTransaction(userId, new TransactionCreateDTO("CDB", TransactionType.BUY, null, null,
                new BigDecimal("1000.00"), null, LocalDate.of(2026, 9, 1), null, null, null,
                issuerEntity("BTG"), LocalDate.of(2030, 1, 15), RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("110.0000"), DISTRIBUTOR, null));

            ArgumentCaptor<FixedIncomeNote> captor = ArgumentCaptor.forClass(FixedIncomeNote.class);
            verify(fixedIncomeNoteRepository).save(captor.capture());
            FixedIncomeNote createdId = captor.getValue();
            assertThat(createdId.getIssuer().getLegalName()).isEqualTo("BTG");
            assertThat(createdId.getDistributor().getId()).isEqualTo(DISTRIBUTOR);
            assertThat(createdId.getIndexerRate()).isEqualByComparingTo("110.0000");
            assertThat(createdId.getMaturityDate()).isEqualTo(LocalDate.of(2030, 1, 15));
        }

        @Test
        @DisplayName("O que o formulario nao manda e herdado de asset_terms — e copiado, nao referenciado")
        void assetTermsInheritance() {
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
                issuerEntity("Tesouro Nacional"), null, null, null, new BigDecimal("7.0900"), DISTRIBUTOR, null));

            ArgumentCaptor<FixedIncomeNote> captor = ArgumentCaptor.forClass(FixedIncomeNote.class);
            verify(fixedIncomeNoteRepository).save(captor.capture());
            assertThat(captor.getValue().getMaturityDate()).isEqualTo(LocalDate.of(2050, 8, 15));
            assertThat(captor.getValue().getIndexer()).isEqualTo(Indexer.IPCA);
        }

        @Test
        @DisplayName("Prefixado com indice limpa o indice, em vez de deixar o banco recusar a linha")
        void fixedRateClearsIndex() {
            when(fixedIncomeNoteRepository.save(any(FixedIncomeNote.class))).thenAnswer(i -> {
                FixedIncomeNote n = i.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });

            service.createTransaction(userId, new TransactionCreateDTO("CDB", TransactionType.BUY, null, null,
                new BigDecimal("1000.00"), null, LocalDate.of(2026, 9, 1), null, null, null,
                issuerEntity("BTG"), LocalDate.of(2030, 1, 15), RateType.PREFIXADO, Indexer.CDI, new BigDecimal("12.5000"), DISTRIBUTOR, null));

            ArgumentCaptor<FixedIncomeNote> captor = ArgumentCaptor.forClass(FixedIncomeNote.class);
            verify(fixedIncomeNoteRepository).save(captor.capture());
            assertThat(captor.getValue().getIndexer()).isNull();
        }

        @Test
        @DisplayName("Pos-fixado sem indice e recusado, com o tipo por extenso na mensagem")
        void floatingWithoutIndex() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, null, issuerEntity("BTG"), LocalDate.of(2030, 1, 15),
                    RateType.POS_FIXADO, null, new BigDecimal("110.0000"), DISTRIBUTOR, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Remuneração pós-fixada precisa de um índice (CDI, IPCA, ...).");
        }

        @Test
        @DisplayName("Indice sem forma de remuneracao e recusado")
        void indexWithoutForm() {
            assertThatThrownBy(() -> service.createTransaction(userId,
                new TransactionCreateDTO("CDB", TransactionType.BUY, null, null, new BigDecimal("1000.00"),
                    null, LocalDate.of(2026, 9, 1), null, null, null, issuerEntity("BTG"), LocalDate.of(2030, 1, 15),
                    null, Indexer.CDI, new BigDecimal("110.0000"), DISTRIBUTOR, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe como o título remunera antes de escolher o índice.");
        }

        @Test
        @DisplayName("Editar so quantidade e valor nao apaga emissor, vencimento e taxa do titulo")
        void editWithoutNoteDataDeletesNothing() {
            FixedIncomeNote note = noteEntity();
            PortfolioAsset pa = existingPosition(cdb, note);
            Transaction existing = Transaction.builder()
                .portfolioAsset(pa).note(note).type(TransactionType.BUY)
                .totalAmount(new BigDecimal("1000.00")).transactionDate(LocalDate.of(2026, 9, 1))
                .build();
            existing.setId(UUID.randomUUID());
            when(transactionRepository.findOwnedById(userId, existing.getId())).thenReturn(Optional.of(existing));

            service.updateTransaction(userId, existing.getId(), new TransactionCreateDTO("CDB",
                TransactionType.BUY, null, null, new BigDecimal("1500.00"), null,
                LocalDate.of(2026, 9, 1), null, null, null, null, null, null, null, null, null, null));

            assertThat(note.getIssuer().getLegalName()).isEqualTo("BTG");
            assertThat(note.getMaturityDate()).isEqualTo(LocalDate.of(2030, 1, 15));
            assertThat(note.getIndexerRate()).isEqualByComparingTo("110.0000");
            verify(fixedIncomeNoteRepository, never()).save(any(FixedIncomeNote.class));
        }

        @Test
        @DisplayName("Editar mandando dados de titulo atualiza o titulo")
        void editWithNoteDataUpdates() {
            FixedIncomeNote note = noteEntity();
            PortfolioAsset pa = existingPosition(cdb, note);
            Transaction existing = Transaction.builder()
                .portfolioAsset(pa).note(note).type(TransactionType.BUY)
                .totalAmount(new BigDecimal("1000.00")).transactionDate(LocalDate.of(2026, 9, 1))
                .build();
            existing.setId(UUID.randomUUID());
            when(transactionRepository.findOwnedById(userId, existing.getId())).thenReturn(Optional.of(existing));

            service.updateTransaction(userId, existing.getId(), new TransactionCreateDTO("CDB",
                TransactionType.BUY, null, null, new BigDecimal("1000.00"), null,
                LocalDate.of(2026, 9, 1), null, null, null, issuerEntity("Inter"), LocalDate.of(2031, 5, 10),
                RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("105.0000"), DISTRIBUTOR, null));

            assertThat(note.getIssuer().getLegalName()).isEqualTo("Inter");
            assertThat(note.getIndexerRate()).isEqualByComparingTo("105.0000");
            verify(fixedIncomeNoteRepository).save(note);
        }
    }

    @Nested
    @DisplayName("Regra K — posicao sem transacao deixa de existir")
    class RuleK {

        private Transaction transactionOf(PortfolioAsset pa, TransactionType typeValue) {
            Transaction tx = Transaction.builder()
                .portfolioAsset(pa).type(typeValue)
                .shares(BigDecimal.TEN).pricePerShare(BigDecimal.TEN).totalAmount(new BigDecimal("100.00"))
                .transactionDate(LocalDate.of(2026, 9, 1))
                .build();
            tx.setId(UUID.randomUUID());
            when(transactionRepository.findOwnedById(userId, tx.getId())).thenReturn(Optional.of(tx));
            return tx;
        }

        private FixedIncomeNote noteEntity(Asset asset) {
            FixedIncomeNote note = FixedIncomeNote.builder().asset(asset).issuer(company("BTG")).build();
            note.setId(UUID.randomUUID());
            return note;
        }

        private void withoutRemainingTransactions(PortfolioAsset pa) {
            when(transactionRepository.findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId()))
                .thenReturn(List.of());
        }

        @Test
        @DisplayName("Regra K — excluir a ultima transacao de uma acao apaga a posicao, sem reavalia-la")
        void ruleK_stock() {
            PortfolioAsset pa = existingPosition(stock, null);
            Transaction tx = transactionOf(pa, TransactionType.BUY);
            withoutRemainingTransactions(pa);

            service.deleteTransaction(userId, tx.getId());

            verify(transactionRepository).delete(tx);
            verify(portfolioAssetRepository).delete(pa);
            verify(fixedIncomeNoteRepository, never()).delete(any());
            verify(appreciationUpdater, never()).refresh(any());
        }

        @Test
        @DisplayName("Regra K — na renda fixa a posicao sai com o titulo, e o titulo depois dela (FK)")
        void ruleK_fixedIncome() {
            FixedIncomeNote note = noteEntity(cdb);
            PortfolioAsset pa = existingPosition(cdb, note);
            Transaction tx = transactionOf(pa, TransactionType.BUY);
            withoutRemainingTransactions(pa);

            service.deleteTransaction(userId, tx.getId());

            InOrder order = inOrder(portfolioAssetRepository, fixedIncomeNoteRepository);
            order.verify(portfolioAssetRepository).delete(pa);
            order.verify(fixedIncomeNoteRepository).delete(note);
            verify(fixedIncomeSettlement, never()).recompute(any(), anyList(), anyBoolean());
        }

        @Test
        @DisplayName("Regra K — com transacao restante a posicao continua e e reavaliada")
        void ruleK_transactionsRemain() {
            PortfolioAsset pa = existingPosition(stock, null);
            Transaction tx = transactionOf(pa, TransactionType.BUY);
            Transaction other = Transaction.builder().portfolioAsset(pa).type(TransactionType.BUY).build();
            when(transactionRepository.findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId()))
                .thenReturn(List.of(other));

            service.deleteTransaction(userId, tx.getId());

            verify(portfolioAssetRepository, never()).delete(any());
            verify(appreciationUpdater).refresh(pa);
        }

        @Test
        @DisplayName("Regra K — no Tesouro o titulo sai mesmo tendo provento registrado")
        void ruleK_treasuryWithDividend() {
            Asset treasury = asset("TESOURO", typeValue("TESOURO_DIRETO", "Tesouro Direto", PriceSource.MANUAL, true));
            FixedIncomeNote note = noteEntity(treasury);
            PortfolioAsset pa = existingPosition(treasury, note);
            pa.getDividends().add(Dividend.builder()
                .portfolioAsset(pa).amountPerShare(BigDecimal.ONE).exDate(LocalDate.of(2026, 9, 5)).build());
            Transaction tx = transactionOf(pa, TransactionType.BUY);
            withoutRemainingTransactions(pa);
            when(transactionRepository.findByPortfolioIdAndAssetId(portfolioEntity.getId(), treasury.getId()))
                .thenReturn(List.of());
            when(treasuryAllocator.salesOf(anyList())).thenReturn(List.of());
            when(portfolioAssetRepository.findAllByPortfolioIdAndAssetId(portfolioEntity.getId(), treasury.getId()))
                .thenReturn(List.of());

            service.deleteTransaction(userId, tx.getId());

            verify(portfolioAssetRepository).delete(pa);
            verify(fixedIncomeNoteRepository).delete(note);
        }
    }
}
