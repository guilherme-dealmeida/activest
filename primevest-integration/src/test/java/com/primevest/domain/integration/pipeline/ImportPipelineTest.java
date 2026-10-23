package com.primevest.domain.integration.pipeline;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.integration.IntegrationCryptoExclusionRepository;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionOrigin;
import com.primevest.domain.transaction.TransactionRepository;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.support.SyntheticAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Set;
import java.util.UUID;

import static com.primevest.support.SyntheticAccount.trade;
import static com.primevest.support.SyntheticAccount.tradeAgainst;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportPipelineTest {

    private static final AssetType CRYPTO = AssetType.builder().id(1L).name("CRIPTO").build();

    @Mock private AssetRepository assets;
    @Mock private TransactionRepository transactions;
    @Mock private IntegrationCryptoExclusionRepository exclusions;

    private ImportPipeline pipeline;
    private final UUID connection = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void scenario() {
        pipeline = new ImportPipeline(assets, transactions, exclusions);
        for (String t : List.of("BTC", "ETH", "USDT")) {
            when(assets.findByTicker(t))
                .thenReturn(Optional.of(Asset.builder().id((long) t.hashCode()).ticker(t).assetType(CRYPTO).build()));
        }
        when(assets.findByTicker("NAOEXISTE")).thenReturn(Optional.empty());
        when(transactions.findImportedIds(eq(connection), anyCollection())).thenReturn(Set.of());
        when(exclusions.findExcludedIds(eq(connection), anyCollection())).thenReturn(Set.of());
    }

    @Test
    @DisplayName("Conciliacao da conta sintetica: as transacoes propostas reproduzem os saldos de BTC, ETH e BRL")
    void reconciliation() {
        ImportPreview p = pipeline.preview(connection, user, SyntheticAccount.movements(), CryptoImportRules.INSTANCE);

        assertThat(p.count(PreviewOutcome.NEW)).isEqualTo(4);
        assertThat(p.count(PreviewOutcome.UNMAPPED)).isZero();
        assertThat(p.fiatDeposited()).isEqualByComparingTo(SyntheticAccount.DEPOSITED);

        Map<String, BigDecimal> position = new HashMap<>();
        BigDecimal reais = p.fiatDeposited();
        for (PreviewItem i : p.of(PreviewOutcome.NEW)) {
            ProposedTransaction t = i.transaction();
            boolean buy = t.type() == TransactionType.BUY;
            position.merge(t.ticker(), buy ? t.shares() : t.shares().negate(), BigDecimal::add);
            // Compra: sai total + taxa. Venda: entra total - taxa.
            reais = buy
                ? reais.subtract(t.totalAmount()).subtract(t.brokerageFee())
                : reais.add(t.totalAmount()).subtract(t.brokerageFee());
        }

        assertThat(position.get("BTC")).isEqualByComparingTo(SyntheticAccount.BALANCE_BTC);
        assertThat(position.get("ETH")).isEqualByComparingTo(SyntheticAccount.BALANCE_ETH);
        // Um centavo por operacao, no maximo (4.5).
        assertThat(reais.subtract(SyntheticAccount.BALANCE_BRL).abs())
            .isLessThanOrEqualTo(CryptoImportRules.TOLERANCE.multiply(BigDecimal.valueOf(4)));
    }

    @Test
    @DisplayName("Ja importado vira EXISTING e excluido pelo usuario vira EXCLUDED, antes de qualquer regra")
    void existingAndExcluded() {
        when(transactions.findImportedIds(eq(connection), anyCollection())).thenReturn(Set.of("teste:spot:BTCBRL:1"));
        when(exclusions.findExcludedIds(eq(connection), anyCollection())).thenReturn(Set.of("teste:convert:Q3"));

        ImportPreview p = pipeline.preview(connection, user, SyntheticAccount.movements(), CryptoImportRules.INSTANCE);

        assertThat(p.of(PreviewOutcome.EXISTING)).extracting(PreviewItem::externalId)
            .containsExactly("teste:spot:BTCBRL:1");
        assertThat(p.of(PreviewOutcome.EXCLUDED)).extracting(PreviewItem::externalId)
            .containsExactly("teste:convert:Q3");
        assertThat(p.count(PreviewOutcome.NEW)).isEqualTo(2);
    }

    @Test
    @DisplayName("Nao importadas ficam na pre-visualizacao com o motivo; nada e descartado em silencio")
    void notImportedWithReason() {
        List<ImportedMovement> batch = new ArrayList<>(SyntheticAccount.movements());
        batch.add(tradeAgainst("teste:spot:ETHBTC:5", "ETH", MovementSide.BUY, "0.1", "BTC", "0.005", null, null,
            "2024-03-02T12:00:00Z"));
        batch.add(trade("teste:spot:X:6", "NAOEXISTE", MovementSide.BUY, "1", "10.00", null, null,
            "2024-03-03T12:00:00Z"));
        batch.add(new ImportedMovement("teste:reward:7", MovementKind.REWARD, "BTC", MovementSide.BUY,
            new BigDecimal("0.00000100"), "BRL", new BigDecimal("0.40"), null, null,
            java.time.Instant.parse("2024-03-04T12:00:00Z"), "{}"));

        ImportPreview p = pipeline.preview(connection, user, batch, CryptoImportRules.INSTANCE);

        assertThat(p.count(PreviewOutcome.NEW)).isEqualTo(4);
        assertThat(p.of(PreviewOutcome.UNMAPPED))
            .extracting(PreviewItem::externalId)
            .containsExactly("teste:spot:ETHBTC:5", "teste:spot:X:6", "teste:reward:7");
        assertThat(p.of(PreviewOutcome.UNMAPPED)).allSatisfy(i -> assertThat(i.reason()).isNotBlank());
    }

    @Test
    @DisplayName("externalId repetido no lote conta uma vez; o catalogo e consultado uma vez por ticker")
    void repeatedAndCache() {
        List<ImportedMovement> batch = new ArrayList<>(SyntheticAccount.movements());
        batch.add(SyntheticAccount.movements().get(2)); // mesma compra #1 de novo

        ImportPreview p = pipeline.preview(connection, user, batch, CryptoImportRules.INSTANCE);

        assertThat(p.count(PreviewOutcome.NEW)).isEqualTo(4);
        verify(assets, times(1)).findByTicker("BTC");
        verify(assets, times(1)).findByTicker("ETH");
    }

    @Test
    @DisplayName("6.6: lancamento manual parecido (mesmo ativo e tipo, um dia de diferenca, quantidade bruta) vira POSSIBLE_DUPLICATE")
    void duplicateOfManual() {
        Asset btc = assets.findByTicker("BTC").orElseThrow();
        PortfolioAsset pa = PortfolioAsset.builder().asset(btc).build();
        Transaction manual = Transaction.builder().id(UUID.randomUUID()).portfolioAsset(pa)
            .type(TransactionType.BUY).shares(new BigDecimal("0.00200000")).pricePerShare(new BigDecimal("400000"))
            .transactionDate(java.time.LocalDate.of(2024, 1, 6)).origin(TransactionOrigin.MANUAL).build();
        Transaction otherDate = Transaction.builder().id(UUID.randomUUID()).portfolioAsset(pa)
            .type(TransactionType.BUY).shares(new BigDecimal("0.00200000")).pricePerShare(new BigDecimal("400000"))
            .transactionDate(java.time.LocalDate.of(2024, 1, 20)).origin(TransactionOrigin.MANUAL).build();
        when(transactions.findByOriginForAssets(eq(user), eq(TransactionOrigin.MANUAL), anyCollection(), any(), any()))
            .thenReturn(List.of(manual, otherDate));

        ImportPreview p = pipeline.preview(connection, user, SyntheticAccount.movements(), CryptoImportRules.INSTANCE);

        assertThat(p.of(PreviewOutcome.POSSIBLE_DUPLICATE)).singleElement().satisfies(i -> {
            assertThat(i.externalId()).isEqualTo("teste:spot:BTCBRL:1");
            assertThat(i.duplicateOf().transactionId()).isEqualTo(manual.getId());
            assertThat(i.transaction()).isNotNull();
        });
        assertThat(p.count(PreviewOutcome.NEW)).isEqualTo(3);
    }

    @Test
    @DisplayName("6.6: tipo diferente ou quantidade fora da taxa nao e duplicata")
    void notDuplicate() {
        Asset btc = assets.findByTicker("BTC").orElseThrow();
        PortfolioAsset pa = PortfolioAsset.builder().asset(btc).build();
        Transaction sale = Transaction.builder().id(UUID.randomUUID()).portfolioAsset(pa)
            .type(TransactionType.SELL).shares(new BigDecimal("0.00199800")).transactionDate(java.time.LocalDate.of(2024, 1, 5))
            .origin(TransactionOrigin.MANUAL).build();
        Transaction otherQty = Transaction.builder().id(UUID.randomUUID()).portfolioAsset(pa)
            .type(TransactionType.BUY).shares(new BigDecimal("0.00300000")).transactionDate(java.time.LocalDate.of(2024, 1, 5))
            .origin(TransactionOrigin.MANUAL).build();
        when(transactions.findByOriginForAssets(eq(user), eq(TransactionOrigin.MANUAL), anyCollection(), any(), any()))
            .thenReturn(List.of(sale, otherQty));

        ImportPreview p = pipeline.preview(connection, user, SyntheticAccount.movements(), CryptoImportRules.INSTANCE);

        assertThat(p.count(PreviewOutcome.POSSIBLE_DUPLICATE)).isZero();
    }

    @Test
    @DisplayName("Lote so de depositos em reais: nenhuma consulta de ids, nenhuma linha")
    void onlyDeposits() {
        ImportPreview p = pipeline.preview(connection, user, List.of(
            SyntheticAccount.deposit("teste:fiat:1", "100.00", "2024-01-01T12:00:00Z")), CryptoImportRules.INSTANCE);

        assertThat(p.items()).isEmpty();
        assertThat(p.fiatDeposited()).isEqualByComparingTo("100.00");
        verify(transactions, never()).findImportedIds(any(), anyCollection());
        verify(exclusions, never()).findExcludedIds(any(), anyCollection());
    }
}
