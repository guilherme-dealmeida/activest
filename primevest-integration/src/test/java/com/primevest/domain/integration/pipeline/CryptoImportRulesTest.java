package com.primevest.domain.integration.pipeline;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.support.ContaSintetica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.primevest.support.ContaSintetica.trade;
import static com.primevest.support.ContaSintetica.tradeContra;
import static org.assertj.core.api.Assertions.assertThat;

/** Regras R2 a R8 sobre dados sinteticos (docs/planos/PLANO_CRIPTO_BINANCE.md, 4.4). */
class CryptoImportRulesTest {

    private static final AssetType CRIPTO = AssetType.builder().id(1L).name("CRIPTO").build();
    private static final AssetType ACAO = AssetType.builder().id(2L).name("ACAO").build();

    private static final Map<String, Asset> CATALOGO = Map.of(
        "BTC", Asset.builder().id(10L).ticker("BTC").assetType(CRIPTO).build(),
        "ETH", Asset.builder().id(11L).ticker("ETH").assetType(CRIPTO).build(),
        "USDT", Asset.builder().id(12L).ticker("USDT").assetType(CRIPTO).build(),
        "PETR4", Asset.builder().id(20L).ticker("PETR4").assetType(ACAO).build());

    private static final Function<String, Optional<Asset>> CATALOGO_FN = t -> Optional.ofNullable(CATALOGO.get(t));

    private static ImportRules.Result map(ImportedMovement m) {
        return CryptoImportRules.map(m, CATALOGO_FN);
    }

    @Nested
    class TaxaR2 {

        @Test
        @DisplayName("Taxa no proprio ativo: quantidade liquida e taxa em reais; total + taxa = o que foi pago")
        void taxaNoAtivo() {
            var r = map(trade("t:1", "BTC", MovementSide.BUY, "0.00200000", "800.00", "0.00000200", "BTC",
                "2024-01-05T15:00:00Z"));

            ProposedTransaction t = r.transaction();
            assertThat(t.type()).isEqualTo(TransactionType.BUY);
            assertThat(t.shares()).isEqualByComparingTo("0.00199800");
            assertThat(t.pricePerShare()).isEqualByComparingTo("400000");
            assertThat(t.brokerageFee()).isEqualByComparingTo("0.80");
            assertThat(t.totalAmount().add(t.brokerageFee())).isEqualByComparingTo("800.00");
            assertThat(t.roundingDifference()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Taxa em reais: quantidade cheia e a taxa como veio")
        void taxaEmReais() {
            var t = map(trade("t:2", "ETH", MovementSide.BUY, "0.05", "600.00", "0.60", "BRL",
                "2024-02-01T15:00:00Z")).transaction();

            assertThat(t.shares()).isEqualByComparingTo("0.05");
            assertThat(t.brokerageFee()).isEqualByComparingTo("0.60");
            assertThat(t.totalAmount()).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("Venda com taxa em reais vira SELL com a quantidade cheia")
        void vendaComTaxaEmReais() {
            var t = map(trade("t:4", "BTC", MovementSide.SELL, "0.0005", "210.00", "0.21", "BRL",
                "2024-03-01T15:00:00Z")).transaction();

            assertThat(t.type()).isEqualTo(TransactionType.SELL);
            assertThat(t.shares()).isEqualByComparingTo("0.0005");
            assertThat(t.brokerageFee()).isEqualByComparingTo("0.21");
        }

        @Test
        @DisplayName("Venda com taxa no proprio ativo: nao importa (caso nao observado)")
        void vendaComTaxaNoAtivo() {
            var r = map(trade("t:5", "BTC", MovementSide.SELL, "0.0005", "210.00", "0.0000005", "BTC",
                "2024-03-01T15:00:00Z"));

            assertThat(r.mapped()).isFalse();
            assertThat(r.reason()).contains("não observado");
        }
    }

    @Test
    @DisplayName("R3: data de negocio no fuso de Brasilia; o instante fica em UTC")
    void fusoDeBrasilia() {
        // 01:30 UTC do dia 6 = 22:30 do dia 5 em Brasilia.
        var t = map(trade("t:6", "BTC", MovementSide.BUY, "0.001", "400.00", null, null,
            "2024-01-06T01:30:00Z")).transaction();

        assertThat(t.transactionDate()).isEqualTo(LocalDate.of(2024, 1, 5));
        assertThat(t.executedAt()).isEqualTo(LocalDateTime.of(2024, 1, 6, 1, 30));
    }

    @Test
    @DisplayName("R4: Convert com taxa embutida — preco = reais / quantidade, taxa zero, total bate ao centavo")
    void convert() {
        ImportedMovement c = ContaSintetica.movimentos().stream()
            .filter(m -> m.kind() == MovementKind.CONVERT).findFirst().orElseThrow();

        var t = map(c).transaction();
        assertThat(t.shares()).isEqualByComparingTo("0.00036123");
        assertThat(t.brokerageFee()).isEqualByComparingTo("0");
        assertThat(t.pricePerShare().scale()).isEqualTo(8);
        assertThat(t.totalAmount()).isEqualByComparingTo("150.00");
        assertThat(t.roundingDifference().abs()).isLessThanOrEqualTo(CryptoImportRules.TOLERANCE);
    }

    @Nested
    class NaoImportadas {

        @Test
        @DisplayName("R6: cripto contra cripto nao tem valor em reais")
        void criptoContraCripto() {
            var r = map(tradeContra("t:7", "ETH", MovementSide.BUY, "0.1", "BTC", "0.005", null, null,
                "2024-01-01T12:00:00Z"));
            assertThat(r.reason()).contains("Sem valor em reais").contains("BTC");
        }

        @Test
        @DisplayName("R6: par contra stablecoin tambem fica de fora (USDT e cripto, D3)")
        void contraStablecoin() {
            var r = map(tradeContra("t:8", "BTC", MovementSide.BUY, "0.001", "USDT", "65.00", null, null,
                "2024-01-01T12:00:00Z"));
            assertThat(r.reason()).contains("USDT");
        }

        @Test
        @DisplayName("R7: ticker fora do catalogo nao importa e nao cria ativo")
        void foraDoCatalogo() {
            var r = map(trade("t:9", "NAOEXISTE", MovementSide.BUY, "1", "10.00", null, null,
                "2024-01-01T12:00:00Z"));
            assertThat(r.reason()).contains("NAOEXISTE").contains("catálogo");
        }

        @Test
        @DisplayName("R7: ticker do catalogo que nao e cripto nao importa")
        void naoECripto() {
            var r = map(trade("t:10", "PETR4", MovementSide.BUY, "1", "30.00", null, null,
                "2024-01-01T12:00:00Z"));
            assertThat(r.reason()).contains("não é criptomoeda");
        }

        @Test
        @DisplayName("R8: taxa em outro ativo (BNB) precisa do preco dele em reais")
        void taxaEmOutroAtivo() {
            var r = map(trade("t:11", "BTC", MovementSide.BUY, "0.001", "400.00", "0.0001", "BNB",
                "2024-01-01T12:00:00Z"));
            assertThat(r.reason()).contains("BNB").contains("R8");
        }

        @Test
        @DisplayName("Transferencias, rendimentos e depositos em reais nao viram transacao")
        void outrosTipos() {
            Instant agora = Instant.parse("2024-01-01T12:00:00Z");
            for (MovementKind k : new MovementKind[] {MovementKind.CRYPTO_DEPOSIT, MovementKind.CRYPTO_WITHDRAW,
                                                      MovementKind.REWARD, MovementKind.FIAT_DEPOSIT}) {
                var r = map(new ImportedMovement("t:" + k, k, "BTC", MovementSide.BUY, BigDecimal.ONE, "BRL",
                    BigDecimal.TEN, null, null, agora, "{}"));
                assertThat(r.mapped()).as(k.name()).isFalse();
            }
        }

        @Test
        @DisplayName("4.5: diferenca acima de um centavo entre o calculado e o cobrado nao importa")
        void diferencaAcimaDoAceito() {
            // 1 bilhao de unidades a um preco de 9 casas: com 8 casas no preco o
            // total foge mais de um centavo — limite conhecido (6.12).
            var r = map(trade("t:12", "USDT", MovementSide.BUY, "1000000000", "123.456789012", null, null,
                "2024-01-01T12:00:00Z"));
            assertThat(r.mapped()).isFalse();
            assertThat(r.reason()).contains("Diferença");
        }
    }
}
