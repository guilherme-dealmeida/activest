package com.primevest.integration.b3investidor;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.integration.pipeline.ImportRules;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.domain.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras B1–B10 (docs/planos/PLANO_INTEGRACAO_B3.md, 6.6) — puras, catalogo inventado. */
class B3EquityImportRulesTest {

    private static final AssetType ACAO = AssetType.builder().id(1L).name("ACAO").priceSource(PriceSource.BRAPI).build();
    private static final AssetType FII = AssetType.builder().id(2L).name("FII").priceSource(PriceSource.BRAPI).build();
    private static final AssetType TESOURO = AssetType.builder().id(4L).name("TESOURO_DIRETO")
        .priceSource(PriceSource.MANUAL).build();

    private static final Map<String, Asset> CATALOGO = Map.of(
        "PETR4", Asset.builder().id(20L).ticker("PETR4").assetType(ACAO).build(),
        "HGLG11", Asset.builder().id(21L).ticker("HGLG11").assetType(FII).build(),
        "Tesouro Selic 2029", Asset.builder().id(30L).ticker("Tesouro Selic 2029").assetType(TESOURO).build());
    private static final Function<String, Optional<Asset>> CATALOGO_FN = t -> Optional.ofNullable(CATALOGO.get(t));

    private static ImportedMovement negocio(String ticker, MovementSide lado, String mercado, String qtd, String valor) {
        LocalDate dia = LocalDate.of(2024, 3, 4);
        return new ImportedMovement("b3:negociacao:2024-03-04:" + B3EquityImportRules.baseTicker(ticker) + ":"
            + (lado == MovementSide.BUY ? "C" : "V"), MovementKind.EQUITY_TRADE, ticker, lado,
            qtd == null ? null : new BigDecimal(qtd), "BRL", valor == null ? null : new BigDecimal(valor), null, null,
            dia.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(), "{}", mercado, "CORRETORA FICTICIA S.A.", dia);
    }

    private static ImportedMovement movimento(MovementKind kind, String tipo) {
        LocalDate dia = LocalDate.of(2024, 6, 14);
        return new ImportedMovement("b3:movimentacao:x", kind, "HGLG11", MovementSide.BUY, BigDecimal.TEN, "BRL",
            new BigDecimal("11.00"), null, null, dia.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(), "{}",
            tipo, null, dia);
    }

    private static ImportRules.Result aplicar(ImportedMovement m) {
        return B3EquityImportRules.INSTANCE.apply(m, CATALOGO_FN);
    }

    @Test
    @DisplayName("B1/B2: o grupo vira uma transacao na data do negocio, com preco medio e sem corretagem (D5)")
    void grupoViraTransacao() {
        ImportRules.Result r = aplicar(negocio("PETR4", MovementSide.BUY, "Mercado à Vista", "107", "4119.64"));

        assertThat(r.mapped()).isTrue();
        assertThat(r.transaction().ticker()).isEqualTo("PETR4");
        assertThat(r.transaction().type()).isEqualTo(TransactionType.BUY);
        assertThat(r.transaction().shares()).isEqualByComparingTo("107");
        assertThat(r.transaction().pricePerShare()).isEqualByComparingTo("38.50130841");
        assertThat(r.transaction().totalAmount()).isEqualByComparingTo("4119.64");
        assertThat(r.transaction().brokerageFee()).isEqualByComparingTo("0");
        assertThat(r.transaction().transactionDate()).isEqualTo(LocalDate.of(2024, 3, 4));
        assertThat(r.transaction().externalId()).isEqualTo("b3:negociacao:2024-03-04:PETR4:C");
    }

    @Test
    @DisplayName("B3: o fracionario (PETR4F) e o mesmo papel do lote padrao")
    void fracionario() {
        ImportRules.Result r = aplicar(negocio("PETR4F", MovementSide.SELL, "Mercado Fracionário", "7", "269.64"));

        assertThat(r.mapped()).isTrue();
        assertThat(r.transaction().ticker()).isEqualTo("PETR4");
        assertThat(r.transaction().type()).isEqualTo(TransactionType.SELL);
        assertThat(B3EquityImportRules.baseTicker("HGLG11")).isEqualTo("HGLG11");
        assertThat(B3EquityImportRules.baseTicker("petr4f ")).isEqualTo("PETR4");
    }

    @Test
    @DisplayName("B4: ticker fora do catalogo nao importa e nao cria ativo")
    void foraDoCatalogo() {
        ImportRules.Result r = aplicar(negocio("ZZZZ3", MovementSide.BUY, "Mercado à Vista", "5", "50.00"));

        assertThat(r.mapped()).isFalse();
        assertThat(r.reason()).contains("ZZZZ3").contains("catálogo");
    }

    @Test
    @DisplayName("B4: ativo do catalogo que nao e renda variavel listada nao importa")
    void naoEhRendaVariavel() {
        assertThat(B3EquityImportRules.INSTANCE.tracksPosition(CATALOGO.get("Tesouro Selic 2029"))).isFalse();
        assertThat(B3EquityImportRules.INSTANCE.tracksPosition(CATALOGO.get("HGLG11"))).isTrue();
    }

    @Test
    @DisplayName("B6: opcao, termo e futuro nao importam")
    void derivativo() {
        ImportRules.Result r = aplicar(negocio("PETRG400", MovementSide.BUY, "Opção de Compra", "100", "50.00"));

        assertThat(r.mapped()).isFalse();
        assertThat(r.reason()).contains("Opção de Compra").contains("B6");
    }

    @Test
    @DisplayName("Negocio sem quantidade ou sem valor nao importa")
    void incompleto() {
        assertThat(aplicar(negocio("PETR4", MovementSide.BUY, "Mercado à Vista", null, "10")).mapped()).isFalse();
        assertThat(aplicar(negocio("PETR4", MovementSide.BUY, "Mercado à Vista", "10", null)).mapped()).isFalse();
        assertThat(aplicar(negocio("PETR4", null, "Mercado à Vista", "10", "10")).mapped()).isFalse();
    }

    @ParameterizedTest(name = "{0} ({1}) nao vira transacao")
    @CsvSource({
        "SETTLEMENT,Transferência - Liquidação,B7",
        "STOCK_EVENT,Bonificação em Ativos,B8",
        "STOCK_EVENT,Atualização,não muda a posição",
        "CASH_EVENT,Rendimento,B8",
        "RIGHTS,Direito de Subscrição,B9",
        "CUSTODY_TRANSFER,Transferência,B10",
        "LENDING,Empréstimo,B10",
        "FIXED_INCOME,COMPRA / VENDA,não traz taxa",
        "OTHER,Resgate,sem regra"
    })
    @DisplayName("B7–B10: o resto da B3 e conferencia, com o motivo")
    void conferencia(MovementKind kind, String tipo, String motivo) {
        ImportRules.Result r = aplicar(movimento(kind, tipo));

        assertThat(r.mapped()).isFalse();
        assertThat(r.reason()).contains(motivo);
    }

    private static ImportedMovement tesouro(String titulo, String tipo, String qtd, String valor) {
        LocalDate dia = LocalDate.of(2025, 3, 26);
        return new ImportedMovement("b3:movimentacao:2025-03-26:" + titulo, MovementKind.FIXED_INCOME, titulo,
            "Venda".equals(tipo) ? MovementSide.SELL : MovementSide.BUY, new BigDecimal(qtd), "BRL", new BigDecimal(valor),
            null, null, dia.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(), "{}", tipo, null, dia);
    }

    @Test
    @DisplayName("F7: compra de Tesouro vira transacao do titulo do catalogo, preco = valor / quantidade")
    void tesouroDireto() {
        ImportRules.Result compra = aplicar(tesouro("Tesouro Selic 2029", "Compra", "1.5", "3118.42"));

        assertThat(compra.mapped()).isTrue();
        assertThat(compra.transaction().ticker()).isEqualTo("Tesouro Selic 2029");
        assertThat(compra.transaction().type()).isEqualTo(TransactionType.BUY);
        assertThat(compra.transaction().pricePerShare()).isEqualByComparingTo("2078.94666667");
        assertThat(compra.transaction().totalAmount()).isEqualByComparingTo("3118.42");
    }

    @Test
    @DisplayName("F7: venda de Tesouro nao importa — o PrimeVest a reparte entre os titulos (FIFO)")
    void vendaDeTesouro() {
        ImportRules.Result venda = aplicar(tesouro("Tesouro Selic 2029", "Venda", "2.52", "40913.87"));

        assertThat(venda.mapped()).isFalse();
        assertThat(venda.reason()).contains("lance-a pela tela");
    }

    @Test
    @DisplayName("F7: outro lancamento de Tesouro, titulo fora do catalogo e CDB nao importam, com o motivo")
    void tesouroNaoImporta() {
        assertThat(aplicar(tesouro("Tesouro Selic 2029", "Juros", "1", "10")).reason()).contains("só a compra");
        assertThat(aplicar(tesouro("Tesouro Educa+ 2040", "Compra", "1", "10")).reason()).contains("catálogo");
        assertThat(aplicar(tesouro("CDB - CDB123 - BANCO FICTICIO", "COMPRA / VENDA", "1", "1000")).reason())
            .contains("não traz taxa");
    }

    @Test
    @DisplayName("Saldo vazio da B3 = Posicao nao veio, nao 'carteira vazia' (sem divergencia)")
    void saldoVazio() {
        assertThat(B3EquityImportRules.INSTANCE.emptyBalanceMeansNoPosition()).isFalse();
        assertThat(B3EquityImportRules.INSTANCE.cashBalances()).isEmpty();
    }
}
