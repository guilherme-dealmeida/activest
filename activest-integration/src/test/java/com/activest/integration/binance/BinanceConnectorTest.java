package com.activest.integration.binance;

import com.activest.domain.integration.credential.ApiKeyCredentials;
import com.activest.domain.integration.source.CredentialCheck;
import com.activest.domain.integration.source.FetchResult;
import com.activest.domain.integration.source.ImportedMovement;
import com.activest.domain.integration.source.MovementKind;
import com.activest.domain.integration.source.MovementSide;
import com.activest.support.BinanceFake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** O conector contra a conta ficticia do {@link BinanceFake}. */
class BinanceConnectorTest {

    private static final ApiKeyCredentials KEY = new ApiKeyCredentials("chave-ficticia-AB12", "segredo-ficticio");

    private BinanceClient client;
    private BinanceConnector connector;

    @BeforeEach
    void scenario() {
        client = mock(BinanceClient.class);
        connector = new BinanceConnector(client, "2024-01-01", "2024-01-01", 0,
            List.of("BRL", "USDT", "BTC"), List.of("BTC"));
        connector.clock = () -> Instant.parse("2024-04-01T00:00:00Z").toEpochMilli();
    }

    private ImportedMovement per(FetchResult r, String id) {
        return r.movements().stream().filter(m -> m.externalId().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("movimento ausente: " + id));
    }

    @Test
    @DisplayName("Chave so de leitura e aceita; o JSON das permissoes vai junto para a credencial")
    void readOnlyKey() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);

        CredentialCheck c = connector.check(KEY);
        assertThat(c.usable()).isTrue();
        assertThat(c.permissions()).contains("\"enableReading\":true");
    }

    @Test
    @DisplayName("Chave que negocia: aceita pela Binance, recusada pelo Activest, com o motivo em portugues")
    void tradingKey() {
        BinanceFake.account(client, BinanceFake.TRADES);

        CredentialCheck c = connector.check(KEY);
        assertThat(c.accepted()).isTrue();
        assertThat(c.readOnly()).isFalse();
        assertThat(c.problem()).contains("negociar").contains("Enable Reading");
    }

    @Test
    @DisplayName("Chave recusada pela Binance (-2015) vira accepted=false, sem excecao")
    void rejectedKey() {
        BinanceFake.rejectedKey(client);

        CredentialCheck c = connector.check(KEY);
        assertThat(c.accepted()).isFalse();
        assertThat(c.problem()).contains("recusou");
    }

    @Test
    @DisplayName("R1 e R5: os dois pedacos da ordem 5001 viram um movimento, com id estavel e taxa somada")
    void groupsChunks() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);

        FetchResult r = connector.fetch(KEY, Map.of());

        ImportedMovement o = per(r, "binance:spot:BTCBRL:5001");
        assertThat(o.kind()).isEqualTo(MovementKind.TRADE);
        assertThat(o.side()).isEqualTo(MovementSide.BUY);
        assertThat(o.asset()).isEqualTo("BTC");
        assertThat(o.quoteAsset()).isEqualTo("BRL");
        assertThat(o.grossQuantity()).isEqualByComparingTo("0.002");
        assertThat(o.quoteAmount()).isEqualByComparingTo("800.10");
        assertThat(o.feeAmount()).isEqualByComparingTo("0.000002");
        assertThat(o.feeAsset()).isEqualTo("BTC");
        assertThat(o.executedAt()).isEqualTo(Instant.ofEpochMilli(BinanceFake.T_ORDER_1));
        assertThat(o.raw()).contains("\"fills\"");

        ImportedMovement sale = per(r, "binance:spot:BTCBRL:5002");
        assertThat(sale.side()).isEqualTo(MovementSide.SELL);
        assertThat(sale.feeAsset()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("Convert, Comprar Cripto, deposito PIX e deposito de cripto viram os movimentos certos; falhas ficam de fora")
    void otherFlows() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);

        FetchResult r = connector.fetch(KEY, Map.of());

        ImportedMovement c1 = per(r, "binance:convert:C1");
        assertThat(c1.kind()).isEqualTo(MovementKind.CONVERT);
        assertThat(c1.asset()).isEqualTo("ETH");
        assertThat(c1.quoteAsset()).isEqualTo("BTC");
        assertThat(c1.grossQuantity()).isEqualByComparingTo("0.003");

        ImportedMovement p1 = per(r, "binance:fiatpay:P1");
        assertThat(p1.kind()).isEqualTo(MovementKind.TRADE);
        assertThat(p1.quoteAmount()).isEqualByComparingTo("100.00"); // 101,00 sem a taxa de 1,00
        assertThat(p1.feeAsset()).isEqualTo("BRL");

        assertThat(per(r, "binance:fiat:F1").kind()).isEqualTo(MovementKind.FIAT_DEPOSIT);
        assertThat(per(r, "binance:deposit:D1").kind()).isEqualTo(MovementKind.CRYPTO_DEPOSIT);

        assertThat(r.movements()).extracting(ImportedMovement::externalId)
            .doesNotContain("binance:convert:C2", "binance:fiat:F2");
        assertThat(r.movements()).hasSize(6);
        // Saldo da conta, para o aviso de divergencia (E3).
        assertThat(r.balances()).containsKeys("BTC", "ETH", "BRL");
        assertThat(r.balances().get("BTC")).isEqualByComparingTo("0.001698");
    }

    @Test
    @DisplayName("Cursores: ultimo id por par e ultimo instante por fluxo; a busca seguinte comeca depois deles")
    void cursorMap() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);

        FetchResult r = connector.fetch(KEY, Map.of());
        assertThat(r.cursors())
            .containsEntry("spot:BTCBRL", "103")
            .containsEntry("convert", Long.toString(BinanceFake.T_CONVERT))
            .containsEntry("fiat_deposits", Long.toString(BinanceFake.T_DEPOSIT))
            .containsEntry("fiat_payments", Long.toString(BinanceFake.T_PAYMENT))
            .containsEntry("crypto_deposits", Long.toString(BinanceFake.T_CRYPTO));

        FetchResult next = connector.fetch(KEY, r.cursors());
        assertThat(next.movements()).extracting(ImportedMovement::externalId)
            .doesNotContain("binance:spot:BTCBRL:5001", "binance:spot:BTCBRL:5002");
        verify(client).signedGet(any(), eq("/api/v3/myTrades"),
            argThat(p -> "BTCBRL".equals(p.get("symbol")) && "104".equals(p.get("fromId").toString())));
    }

    @Test
    @DisplayName("So consulta pares que existem na corretora")
    void onlyExistingPairs() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);

        connector.fetch(KEY, Map.of());

        // BTCBRL, BTCUSDT, ETHBRL e ETHBTC existem; USDT (do deposito) x BRL/BTC nao.
        verify(client, never()).signedGet(any(), eq("/api/v3/myTrades"), argThat(p -> "USDTBRL".equals(p.get("symbol"))));
        verify(client).signedGet(any(), eq("/api/v3/myTrades"), argThat(p -> "ETHBTC".equals(p.get("symbol"))));
    }

    @Test
    @DisplayName("Taxa em duas moedas na mesma ordem sai como BNB+BTC (o pipeline a deixa de fora pela R8)")
    void feeInTwoCurrencies() {
        var f1 = BinanceFake.json("""
            {"id":1,"orderId":7,"qty":"0.001","quoteQty":"400","commission":"0.000001","commissionAsset":"BTC",
             "time":1,"isBuyer":true}""");
        var f2 = BinanceFake.json("""
            {"id":2,"orderId":7,"qty":"0.001","quoteQty":"400","commission":"0.0001","commissionAsset":"BNB",
             "time":2,"isBuyer":true}""");

        ImportedMovement m = connector.order("BTCBRL", "BTC", "BRL", "7", List.of(f1, f2));
        assertThat(m.feeAsset()).isEqualTo("BNB+BTC");
        verify(client, never()).signedGet(any(), any(), anyMap());
    }

    @Test
    @DisplayName("Binance Stocks: a ordem executada vira o ETF e a perna em USDC; a cancelada sem execucao nao entra")
    void stocksOrders() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);
        BinanceFake.stocks(client);
        connector.clock = () -> Instant.parse("2026-07-01T00:00:00Z").toEpochMilli();

        FetchResult r = connector.fetch(KEY, java.util.Map.of());

        ImportedMovement etf = per(r, "binance:equity:E1");
        assertThat(etf.kind()).isEqualTo(com.activest.domain.integration.source.MovementKind.FOREIGN_TRADE);
        assertThat(etf.asset()).isEqualTo("ZZETF");
        assertThat(etf.grossQuantity()).isEqualByComparingTo("2.5");
        assertThat(etf.quoteAsset()).isEqualTo("USDC");
        assertThat(etf.quoteAmount()).isEqualByComparingTo("250.00");
        assertThat(etf.feeAmount()).isEqualByComparingTo("0.35");
        ImportedMovement leg = per(r, "binance:equity:E1:quote");
        assertThat(leg.kind()).isEqualTo(com.activest.domain.integration.source.MovementKind.STABLECOIN_SETTLEMENT);
        assertThat(leg.asset()).isEqualTo("USDC");
        assertThat(leg.side()).isEqualTo(com.activest.domain.integration.source.MovementSide.SELL);
        assertThat(leg.grossQuantity()).isEqualByComparingTo("250.35");
        assertThat(r.movements()).noneMatch(m -> m.externalId().startsWith("binance:equity:E2"));
        assertThat(r.cursors()).containsEntry("equity", Long.toString(BinanceFake.T_EQUITY));
        assertThat(r.balances()).containsEntry("equity:ZZETF", new java.math.BigDecimal("2.500000001"));
        assertThat(connector.rulesFor(etf)).isSameAs(com.activest.domain.integration.pipeline.ForeignImportRules.INSTANCE);
        assertThat(connector.rulesFor(leg)).isSameAs(com.activest.domain.integration.pipeline.CryptoImportRules.INSTANCE);
    }

    @Test
    @DisplayName("Ordem ainda aberta e parcialmente executada nao entra e segura o cursor para a proxima busca")
    void openOrderHoldsCursor() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);
        long created = Instant.parse("2026-06-20T15:00:00Z").toEpochMilli();
        org.mockito.Mockito.when(client.signedGet(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("/sapi/v1/equity/order/history"), org.mockito.ArgumentMatchers.anyMap()))
            .thenAnswer(i -> BinanceFake.json("""
                {"page":1,"size":100,"total":2,"rows":[
                 {"orderId":"E3","symbol":"ZZETF","quote":"USDC","side":"BUY","filledQty":"1","filledTotal":"100",
                  "fee":"0.1","status":"PARTIALLY_FILLED","createdAt":%d,"updatedAt":%d},
                 {"orderId":"E4","symbol":"ZZETF","quote":"USDC","side":"BUY","filledQty":"1","filledTotal":"100",
                  "fee":"0.1","status":"FILLED","createdAt":%d,"updatedAt":%d}]}"""
                .formatted(created, created + 10, created + 3_600_000, created + 3_600_010)));
        connector.clock = () -> Instant.parse("2026-07-01T00:00:00Z").toEpochMilli();

        FetchResult r = connector.fetch(KEY, java.util.Map.of());

        assertThat(r.movements()).anyMatch(m -> m.externalId().equals("binance:equity:E4"));
        assertThat(r.movements()).noneMatch(m -> m.externalId().startsWith("binance:equity:E3"));
        assertThat(r.cursors()).containsEntry("equity", Long.toString(created - 1));
    }

    @Test
    @DisplayName("Conta sem Binance Stocks (a corretora responde 400): a busca de cripto segue sem as acoes")
    void stocksUnavailable() {
        BinanceFake.account(client, BinanceFake.READ_ONLY);
        org.mockito.Mockito.when(client.signedGet(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("/sapi/v1/equity/order/history"), org.mockito.ArgumentMatchers.anyMap()))
            .thenThrow(new BinanceException(400, -1002, "Binance 400 (-1002) em /sapi/v1/equity/order/history: x"));
        connector.clock = () -> Instant.parse("2026-07-01T00:00:00Z").toEpochMilli();

        FetchResult r = connector.fetch(KEY, java.util.Map.of());

        assertThat(r.movements()).anyMatch(m -> m.externalId().startsWith("binance:spot:"));
        assertThat(r.movements()).noneMatch(m -> m.externalId().startsWith("binance:equity:"));
    }
}
