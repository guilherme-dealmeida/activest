package com.activest.domain.integration.pipeline;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetType;
import com.activest.domain.integration.source.ImportedMovement;
import com.activest.domain.integration.source.MovementKind;
import com.activest.domain.integration.source.MovementSide;
import com.activest.domain.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Acoes e ETFs dos EUA (docs/planos/PLANO_INTEGRACOES.md, 10.3), com dados ficticios. */
class ForeignImportRulesTest {

    private static final AssetType ETF = AssetType.builder().id(10L).name("ETF_INTERNACIONAL").build();
    private static final AssetType CRYPTO = AssetType.builder().id(11L).name("CRIPTO").build();

    private static final Asset ZZETF = Asset.builder().id(100L).ticker("ZZETF").assetType(ETF).currencyCode("USD").build();
    private static final Asset ZZETF_CRYPTO = Asset.builder().id(101L).ticker("ZZETF").assetType(CRYPTO)
        .currencyCode("BRL").build();
    private static final Asset USDC = Asset.builder().id(102L).ticker("USDC").assetType(CRYPTO).currencyCode("BRL")
        .referenceCurrencyCode("USD").build();
    private static final Asset BTC = Asset.builder().id(103L).ticker("BTC").assetType(CRYPTO).currencyCode("BRL").build();

    /** O catalogo procurado por tipo, como o AssetResolver: o mesmo ticker existe como ETF e como cripto. */
    private static final ImportContext CONTEXT = new ImportContext() {
        @Override
        public Optional<Asset> asset(String ticker, Set<String> types) {
            Map<String, Asset> etfs = Map.of("ZZETF", ZZETF);
            Map<String, Asset> cryptos = Map.of("ZZETF", ZZETF_CRYPTO, "USDC", USDC, "BTC", BTC);
            if (types.contains("ETF_INTERNACIONAL")) {
                return Optional.ofNullable(etfs.get(ticker));
            }
            return types.contains("CRIPTO") ? Optional.ofNullable(cryptos.get(ticker)) : Optional.empty();
        }

        @Override
        public Optional<BigDecimal> fxRate(String currency, LocalDate date) {
            return Optional.of(new BigDecimal("5.4321"));
        }
    };

    private static ImportedMovement order(String asset, MovementSide side, String qty, String quote, String total,
                                          String fee) {
        return new ImportedMovement("binance:equity:E1", MovementKind.FOREIGN_TRADE, asset, side, new BigDecimal(qty),
            quote, new BigDecimal(total), fee == null ? null : new BigDecimal(fee), quote,
            Instant.parse("2026-06-10T15:00:00Z"), "{}");
    }

    @Test
    @DisplayName("Compra paga em USDC: ETF em USD, preco = total / quantidade, taxa em USD no custo (P1)")
    void buyInStablecoin() {
        var r = ForeignImportRules.INSTANCE.apply(order("ZZETF", MovementSide.BUY, "2.5", "USDC", "250.00", "0.35"),
            CONTEXT);

        assertThat(r.mapped()).isTrue();
        ProposedTransaction t = r.transaction();
        assertThat(t.assetId()).isEqualTo(100L);
        assertThat(t.type()).isEqualTo(TransactionType.BUY);
        assertThat(t.shares()).isEqualByComparingTo("2.5");
        assertThat(t.pricePerShare()).isEqualByComparingTo("100");
        assertThat(t.brokerageFee()).isEqualByComparingTo("0.35");
        assertThat(t.currencyCode()).isEqualTo("USD");
        assertThat(t.transactionDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(t.roundingDifference()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Venda e quantidade de 8 casas (a corretora arredonda): preco em 8 casas, diferenca dentro do aceito")
    void sellEightDecimals() {
        var r = ForeignImportRules.INSTANCE.apply(order("ZZETF", MovementSide.SELL, "1.55913017", "USDC", "122.03",
            "0.17"), CONTEXT);

        assertThat(r.mapped()).isTrue();
        assertThat(r.transaction().type()).isEqualTo(TransactionType.SELL);
        assertThat(r.transaction().pricePerShare()).isEqualByComparingTo("78.26799991");
    }

    @Test
    @DisplayName("Ticker que so existe como cripto nao vira cripto: o ETF e procurado so nos tipos do exterior")
    void onlyForeignTypes() {
        var r = ForeignImportRules.INSTANCE.apply(order("NOPE", MovementSide.BUY, "1", "USDC", "10.00", null), CONTEXT);

        assertThat(r.mapped()).isFalse();
        assertThat(r.reason()).contains("NOPE").contains("catálogo");
    }

    @Test
    @DisplayName("Paga em cripto que nao acompanha o dolar (BTC): nao importa, com o motivo")
    void quoteNotPegged() {
        var r = ForeignImportRules.INSTANCE.apply(order("ZZETF", MovementSide.BUY, "1", "BTC", "0.001", null),
            CONTEXT);

        assertThat(r.mapped()).isFalse();
        assertThat(r.reason()).contains("BTC").contains("USD");
    }

    @Test
    @DisplayName("Conversao em bStocks (D11): nao importada, com o motivo legivel")
    void tokenized() {
        var m = new ImportedMovement("binance:tokenized:X", MovementKind.TOKENIZED_CONVERT, "ZZETF",
            MovementSide.SELL, BigDecimal.ONE, null, null, null, null, Instant.parse("2026-06-11T15:00:00Z"), "{}");

        var r = ForeignImportRules.INSTANCE.apply(m, CONTEXT);

        assertThat(r.mapped()).isFalse();
        assertThat(r.reason()).contains("bStocks");
    }

    @Test
    @DisplayName("Saldo e posicao com a chave do exterior: equity:ZZETF nao se confunde com a cripto ZZETF")
    void balanceKey() {
        assertThat(ForeignImportRules.INSTANCE.balanceKey(ZZETF)).isEqualTo("equity:ZZETF");
        assertThat(ForeignImportRules.INSTANCE.tracksPosition(ZZETF)).isTrue();
        assertThat(ForeignImportRules.INSTANCE.tracksPosition(ZZETF_CRYPTO)).isFalse();
    }

    @Test
    @DisplayName("Perna em USDC (D6): venda de USDC pelo cambio da data (PTAX venda, D10), sem taxa")
    void stablecoinLeg() {
        var m = new ImportedMovement("binance:equity:E1:quote", MovementKind.STABLECOIN_SETTLEMENT, "USDC",
            MovementSide.SELL, new BigDecimal("250.35"), "USD", new BigDecimal("250.35"), null, null,
            Instant.parse("2026-06-10T15:00:00Z"), "{}");

        var r = CryptoImportRules.INSTANCE.apply(m, CONTEXT);

        assertThat(r.mapped()).isTrue();
        assertThat(r.transaction().assetId()).isEqualTo(102L);
        assertThat(r.transaction().type()).isEqualTo(TransactionType.SELL);
        assertThat(r.transaction().shares()).isEqualByComparingTo("250.35");
        assertThat(r.transaction().pricePerShare()).isEqualByComparingTo("5.4321");
        assertThat(r.transaction().brokerageFee()).isEqualByComparingTo("0");
        assertThat(r.transaction().currencyCode()).isNull();
    }

    @Test
    @DisplayName("Perna em USDC sem cambio na data: nao importa e diz por que")
    void stablecoinWithoutRate() {
        ImportContext noRate = ImportContext.of(t -> Optional.of(USDC), (c, d) -> Optional.empty());
        var m = new ImportedMovement("binance:equity:E1:quote", MovementKind.STABLECOIN_SETTLEMENT, "USDC",
            MovementSide.SELL, BigDecimal.TEN, "USD", BigDecimal.TEN, null, null,
            Instant.parse("2026-06-10T15:00:00Z"), "{}");

        var r = CryptoImportRules.INSTANCE.apply(m, noRate);

        assertThat(r.mapped()).isFalse();
        assertThat(r.reason()).contains("câmbio");
    }
}
