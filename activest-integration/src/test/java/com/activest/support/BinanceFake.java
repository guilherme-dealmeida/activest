package com.activest.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.integration.binance.BinanceClient;
import com.activest.integration.binance.BinanceException;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Uma conta da Binance INVENTADA, servida por um BinanceClient mockado — no
 * formato das respostas reais, com ids, datas e valores ficticios.
 *
 * <pre>
 * Deposito PIX F1           1.000,00 BRL (e um F2 que falhou)
 * Ordem 5001 BTCBRL compra  2 pedacos: 0,001 por 400,00 e 0,001 por 400,10; taxa 0,000001 BTC cada
 * Ordem 5002 BTCBRL venda   0,0002 por 84,00; taxa 0,084 BRL
 * Comprar Cripto P1         0,00025 ETH por 101,00 BRL (taxa 1,00 BRL)
 * Convert C1                0,0001 BTC -> 0,003 ETH (cripto x cripto: nao importa, R6)
 * Deposito de cripto D1     50 USDT (transferencia: nao importa, D6)
 * </pre>
 * Esperado na pre-visualizacao: 3 novas, 2 nao importadas, 1.000,00 depositados.
 */
public final class BinanceFake {

    private static final ObjectMapper JSON = new ObjectMapper();

    // Instantes ficticios (fev/mar de 2024).
    public static final long T_DEPOSIT = 1_706_875_200_000L;   // 2024-02-02 12:00 UTC
    public static final long T_ORDER_1 = 1_706_886_000_000L;    // 2024-02-02 15:00 UTC
    public static final long T_PAYMENT = 1_707_307_200_000L;  // 2024-02-07 12:00 UTC
    public static final long T_CONVERT = 1_708_000_000_000L;    // 2024-02-15 12:26 UTC
    public static final long T_CRYPTO = 1_708_500_000_000L;     // 2024-02-21 07:20 UTC
    public static final long T_ORDER_2 = 1_709_305_200_000L;    // 2024-03-01 15:00 UTC

    public static final String READ_ONLY = """
        {"ipRestrict":false,"createTime":1706000000000,"enableReading":true,"enableSpotAndMarginTrading":false,
         "enableWithdrawals":false,"enableInternalTransfer":false,"permitsUniversalTransfer":false,
         "enableMargin":false,"enableFutures":false,"enableVanillaOptions":false,
         "enablePortfolioMarginTrading":false,"enableFixApiTrade":false,"enableFixReadOnly":false}""";

    public static final String TRADES = READ_ONLY
        .replace("\"enableSpotAndMarginTrading\":false", "\"enableSpotAndMarginTrading\":true");

    private BinanceFake() {
    }

    /** Liga o mock a conta ficticia. {@code restrictions}: SO_LEITURA ou NEGOCIA. */
    public static void account(BinanceClient client, String restrictions) {
        when(client.signedGet(any(), eq("/sapi/v1/account/apiRestrictions"), anyMap()))
            .thenAnswer(i -> json(restrictions));
        when(client.signedGet(any(), eq("/api/v3/account"), anyMap())).thenAnswer(i -> json("""
            {"accountType":"SPOT","balances":[{"asset":"BTC","free":"0.00169800","locked":"0.00000000"},
             {"asset":"ETH","free":"0.00325000","locked":"0.00000000"},{"asset":"BRL","free":"66.83","locked":"0"}]}"""));
        when(client.publicGet(eq("/api/v3/exchangeInfo"), anyMap())).thenAnswer(i -> json("""
            {"symbols":[{"symbol":"BTCBRL","baseAsset":"BTC","quoteAsset":"BRL"},
                        {"symbol":"ETHBRL","baseAsset":"ETH","quoteAsset":"BRL"},
                        {"symbol":"BTCUSDT","baseAsset":"BTC","quoteAsset":"USDT"},
                        {"symbol":"ETHBTC","baseAsset":"ETH","quoteAsset":"BTC"}]}"""));
        when(client.signedGet(any(), eq("/api/v3/myTrades"), anyMap())).thenAnswer(i -> {
            Map<?, ?> p = i.getArgument(2);
            if (!"BTCBRL".equals(p.get("symbol")) || Long.parseLong(p.get("fromId").toString()) > 103) {
                return json("[]");
            }
            return json("""
                [{"symbol":"BTCBRL","id":101,"orderId":5001,"price":"400000.00","qty":"0.00100000","quoteQty":"400.00",
                  "commission":"0.00000100","commissionAsset":"BTC","time":%d,"isBuyer":true,"isMaker":false},
                 {"symbol":"BTCBRL","id":102,"orderId":5001,"price":"400100.00","qty":"0.00100000","quoteQty":"400.10",
                  "commission":"0.00000100","commissionAsset":"BTC","time":%d,"isBuyer":true,"isMaker":false},
                 {"symbol":"BTCBRL","id":103,"orderId":5002,"price":"420000.00","qty":"0.00020000","quoteQty":"84.00",
                  "commission":"0.08400000","commissionAsset":"BRL","time":%d,"isBuyer":false,"isMaker":true}]"""
                .formatted(T_ORDER_1, T_ORDER_1 + 50, T_ORDER_2));
        });
        when(client.signedGet(any(), eq("/sapi/v1/fiat/orders"), anyMap())).thenAnswer(i -> json("""
            {"code":"000000","data":[
              {"orderNo":"F1","fiatCurrency":"BRL","indicatedAmount":"1000.00","amount":"1000.00","totalFee":"0",
               "method":"Bank Transfer (PIX)","status":"Successful","createTime":%d},
              {"orderNo":"F2","fiatCurrency":"BRL","indicatedAmount":"50.00","amount":"50.00","totalFee":"0",
               "method":"Bank Transfer (PIX)","status":"Failed","createTime":%d}],"total":2,"success":true}"""
            .formatted(T_DEPOSIT, T_DEPOSIT + 1000)));
        when(client.signedGet(any(), eq("/sapi/v1/fiat/payments"), anyMap())).thenAnswer(i -> json("""
            {"code":"000000","data":[
              {"orderNo":"P1","sourceAmount":"101.00","fiatCurrency":"BRL","obtainAmount":"0.00025000",
               "cryptoCurrency":"ETH","totalFee":"1.00","price":"400000","status":"Completed","createTime":%d}],
             "total":1,"success":true}""".formatted(T_PAYMENT)));
        when(client.signedGet(any(), eq("/sapi/v1/convert/tradeFlow"), anyMap())).thenAnswer(i -> {
            Map<?, ?> p = i.getArgument(2);
            long ini = Long.parseLong(p.get("startTime").toString());
            long end = Long.parseLong(p.get("endTime").toString());
            if (T_CONVERT < ini || T_CONVERT > end) {
                return json("{\"list\":[],\"moreData\":false}");
            }
            return json("""
                {"list":[{"quoteId":"C1","orderId":9223372036854775000,"orderStatus":"SUCCESS","fromAsset":"BTC",
                          "fromAmount":"0.00010000","toAsset":"ETH","toAmount":"0.00300000","createTime":%d},
                         {"quoteId":"C2","orderId":1,"orderStatus":"PROCESS","fromAsset":"BRL","fromAmount":"10",
                          "toAsset":"BTC","toAmount":"0.00002","createTime":%d}],"moreData":false}"""
                .formatted(T_CONVERT, T_CONVERT + 1));
        });
        when(client.signedGet(any(), eq("/sapi/v1/capital/deposit/hisrec"), anyMap())).thenAnswer(i -> {
            Map<?, ?> p = i.getArgument(2);
            long ini = Long.parseLong(p.get("startTime").toString());
            long end = Long.parseLong(p.get("endTime").toString());
            if (T_CRYPTO < ini || T_CRYPTO > end || !"0".equals(p.get("offset").toString())) {
                return json("[]");
            }
            return json("""
                [{"id":"D1","amount":"50","coin":"USDT","network":"TRX","status":1,"txId":"tx-ficticio",
                  "insertTime":%d}]""".formatted(T_CRYPTO));
        });
    }

    /** A Binance recusando a chave (-2015). */
    public static void rejectedKey(BinanceClient client) {
        when(client.signedGet(any(), eq("/sapi/v1/account/apiRestrictions"), anyMap()))
            .thenThrow(new BinanceException(401, -2015, "Binance 401 (-2015) em /sapi/v1/account/apiRestrictions: "
                + "Invalid API-key, IP, or permissions for action."));
    }

    public static JsonNode json(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
