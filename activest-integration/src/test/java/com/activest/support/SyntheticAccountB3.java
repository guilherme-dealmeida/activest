package com.activest.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.integration.b3investidor.B3InvestidorClient;
import com.activest.integration.b3investidor.B3InvestidorException;
import com.activest.integration.b3investidor.B3InvestidorProperties;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * Um investidor INVENTADO na Area do Investidor da B3, servido por um B3InvestidorClient mockado —
 * no formato das respostas da API (docs/planos/PLANO_INTEGRACAO_B3.md, 6.3 e 6.4), com CPF, datas e
 * valores ficticios. E a {@code ContaSintetica} da B3: os testes nao usam o simulador nem dado real.
 *
 * <pre>
 * Negociacao (duas paginas; o PETR4 de 04/03 esta nas duas)
 *   04/03/2024  PETR4   Compra 100 x 38,50 = 3.850,00   Mercado à Vista      ┐ um grupo (B1):
 *   04/03/2024  PETR4F  Compra   7 x 38,52 =   269,64   Mercado Fracionário  ┘ 107 por 4.119,64
 *   04/03/2024  HGLG11  Compra  10 x 160,00 = 1.600,00  Mercado à Vista
 *   10/04/2024  PETRG400 Compra 100 x 0,50  =    50,00  Opção de Compra       -> nao importa (B6)
 *   15/05/2024  PETR4   Venda   30 x 41,00 = 1.230,00   Mercado à Vista
 *   15/05/2024  ZZZZ3   Compra   5 x 10,00 =    50,00   Mercado à Vista      -> fora do catalogo (B4)
 * Movimentacao (conferencia): 3 liquidacoes, rendimento, JCP, Atualizacao, direito de subscricao,
 *   um tipo sem regra e uma compra de Tesouro.
 * Eventos provisionados: um dividendo de PETR4 a receber.
 * Posicao: PETR4 77, HGLG11 10 — o que a negociacao deixa.
 * </pre>
 * Esperado na primeira pre-visualizacao: 4 novas (PETR4 compra, HGLG11 compra, PETR4 venda e a compra de Tesouro
 * Selic 2029, F7) e o resto nao importado.
 */
public final class SyntheticAccountB3 {

    /** CPF ficticio valido — o mesmo padrao do simulador (B3I_MOCK_DOCUMENT). */
    public static final String CPF = "52998224725";
    /** Outro CPF ficticio valido, que a B3 "nao localiza". */
    public static final String UNKNOWN_CPF = "11144477735";

    public static final int NEW_ONES = 4;
    public static final int GROUPS = 5;

    public static final B3InvestidorProperties.Paths PATHS = new B3InvestidorProperties.Paths();

    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private SyntheticAccountB3() {
    }

    /** Liga o mock a conta ficticia. {@code authorized}: o investidor ja deu o opt-in ao Activest. */
    public static void account(B3InvestidorClient client, boolean authorized) {
        authorization(client, authorized);
        // doAnswer: restubar (autorizar, revogar) nao chama a resposta anterior.
        doAnswer(i -> {
            requireAuthorized(i.getArgument(1));
            return List.of(json(TRADE_1), json(TRADE_2));
        }).when(client).getAll(eq(PATHS.getAssetTrading()), anyMap(), anyMap());
        doAnswer(i -> {
            Map<String, String> v = i.getArgument(1);
            requireAuthorized(v);
            return switch (v.get("product")) {
                case "equities" -> List.of(json(MOVEMENTS_STOCKS));
                case "treasury-bonds" -> List.of(json(MOVEMENTS_TREASURY));
                default -> List.of(json("{\"data\":{\"fixedIncomes\":{\"fixedIncomeMovements\":[]}},\"Links\":{}}"));
            };
        }).when(client).getAll(eq(PATHS.getMovement()), anyMap(), anyMap());
        doAnswer(i -> {
            requireAuthorized(i.getArgument(1));
            return List.of(json(PROVISIONED));
        }).when(client).getAll(eq(PATHS.getProvisionedEvents()), anyMap(), anyMap());
        doAnswer(i -> {
            requireAuthorized(i.getArgument(1));
            return List.of(json(POSITION));
        }).when(client).getAll(eq(PATHS.getPosition()), anyMap(), anyMap());
    }

    private static boolean authorizedNow;

    /** Muda a autorizacao do investidor (opt-in ou revogacao na B3). */
    public static void authorization(B3InvestidorClient client, boolean authorized) {
        authorizedNow = authorized;
        doAnswer(i -> {
            Map<String, String> v = i.getArgument(1);
            if (!CPF.equals(v.get("documentNumber"))) {
                throw error("422.03", "CPF/CNPJ não localizado");
            }
            return json("{\"data\":{\"documentNumber\":\"" + CPF + "\",\"authorized\":" + authorizedNow
                + ",\"authorizationDate\":" + (authorizedNow ? "\"2024-02-01T10:00:00\"" : "null") + "}}");
        }).when(client).get(eq(PATHS.getAuthorization()), anyMap(), anyMap());
    }

    private static void requireAuthorized(Map<String, String> vars) {
        if (!CPF.equals(vars.get("documentNumber"))) {
            throw error("422.03", "CPF/CNPJ não localizado");
        }
        if (!authorizedNow) {
            throw error("422.02", "Participante não autorizado acessar CPF/CNPJ");
        }
    }

    public static B3InvestidorException error(String code, String title) {
        return new B3InvestidorException(422, code, "B3 422 (" + code + ") em {documentNumber}: " + title, null);
    }

    public static JsonNode json(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static final String TRADE_1 = """
        {"data":{"documentNumber":"52998224725","periodList":[
          {"referenceDate":"2024-03-04","assetTradingList":[
            {"side":"Compra","marketName":"Mercado à Vista","participantName":"CORRETORA FICTICIA S.A.",
             "tickerSymbol":"HGLG11","tradeQuantity":10,"priceValue":160.00,"totalTradeAmount":1600.00},
            {"side":"Compra","marketName":"Mercado à Vista","participantName":"CORRETORA FICTICIA S.A.",
             "tickerSymbol":"PETR4","tradeQuantity":100,"priceValue":38.50,"totalTradeAmount":3850.00}]}]},
         "Links":{"self":"x?page=1","first":"x?page=1","prev":null,"next":"x?page=2","last":"x?page=2"}}""";

    static final String TRADE_2 = """
        {"data":{"documentNumber":"52998224725","periodList":[
          {"referenceDate":"2024-03-04","assetTradingList":[
            {"side":"Compra","marketName":"Mercado Fracionário","participantName":"CORRETORA FICTICIA S.A.",
             "tickerSymbol":"PETR4F","tradeQuantity":7,"priceValue":38.52,"totalTradeAmount":269.64}]},
          {"referenceDate":"2024-04-10","assetTradingList":[
            {"side":"Compra","marketName":"Opção de Compra","participantName":"CORRETORA FICTICIA S.A.",
             "tickerSymbol":"PETRG400","tradeQuantity":100,"priceValue":0.50,"totalTradeAmount":50.00}]},
          {"referenceDate":"2024-05-15","assetTradingList":[
            {"side":"Venda","marketName":"Mercado à Vista","participantName":"CORRETORA FICTICIA S.A.",
             "tickerSymbol":"PETR4","tradeQuantity":30,"priceValue":41.00,"totalTradeAmount":1230.00},
            {"side":"Compra","marketName":"Mercado à Vista","participantName":"CORRETORA FICTICIA S.A.",
             "tickerSymbol":"ZZZZ3","tradeQuantity":5,"priceValue":10.00,"totalTradeAmount":50.00}]}]},
         "links":{"self":"x?page=2","first":"x?page=1","prev":"x?page=1","next":null,"last":"x?page=2"}}""";

    static final String MOVEMENTS_STOCKS = """
        {"data":{"equitiesPeriods":{"documentNumber":"52998224725","equitiesMovements":[
          {"referenceDate":"2024-03-06","movementType":"Transferência - Liquidação","operationType":"Credito",
           "tickerSymbol":"PETR4","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":107,
           "unitPrice":38.50,"operationValue":4119.64},
          {"referenceDate":"2024-03-06","movementType":"Transferência - Liquidação","operationType":"Credito",
           "tickerSymbol":"HGLG11","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":10,
           "unitPrice":160.00,"operationValue":1600.00},
          {"referenceDate":"2024-05-17","movementType":"Transferência - Liquidação","operationType":"Debito",
           "tickerSymbol":"PETR4","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":30,
           "unitPrice":41.00,"operationValue":1230.00},
          {"referenceDate":"2024-06-14","movementType":"Rendimento","operationType":"Credito",
           "tickerSymbol":"HGLG11","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":10,
           "unitPrice":1.10,"operationValue":11.00},
          {"referenceDate":"2024-06-20","movementType":"Juros Sobre Capital Próprio","operationType":"Credito",
           "tickerSymbol":"PETR4","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":77,
           "unitPrice":0.33,"operationValue":25.41},
          {"referenceDate":"2024-07-01","movementType":"Atualização","operationType":"Credito",
           "tickerSymbol":"PETR4","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":77,
           "unitPrice":null,"operationValue":null},
          {"referenceDate":"2024-07-10","movementType":"Direito de Subscrição","operationType":"Credito",
           "tickerSymbol":"HGLG12","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":2,
           "unitPrice":null,"operationValue":null},
          {"referenceDate":"2024-07-15","movementType":"Resgate","operationType":"Debito",
           "tickerSymbol":"HGLG11","participantName":"CORRETORA FICTICIA S.A.","equitiesQuantity":1,
           "unitPrice":null,"operationValue":null}]}},
         "Links":{"next":null}}""";

    static final String MOVEMENTS_TREASURY = """
        {"data":{"treasuryBondsPeriods":{"documentNumber":"52998224725","treasuryBondsMovements":[
          {"referenceDate":"2024-04-01","movementType":"Compra","operationType":"Credito",
           "assetDescription":"Tesouro Selic 2029","participantName":"CORRETORA FICTICIA S.A.",
           "treasuryBondsQuantity":0.5,"unitPrice":14000.00,"operationValue":7000.00}]}},
         "Links":{"next":null}}""";

    static final String PROVISIONED = """
        {"data":{"provisionedEvents":[
          {"documentNumber":"52998224725","tickerSymbol":"PETR4","corporationName":"PETROLEO FICTICIO",
           "specificationCode":"PN","corporateActionTypeDescription":"Dividendo","PaymentDate":"2024-12-20",
           "participantName":"CORRETORA FICTICIA S.A.","eventQuantity":77,"eventValue":0.40,"netValue":30.80}]},
         "Links":{"next":null}}""";

    static final String POSITION = """
        {"data":{"equitiesPositions":[
          {"tickerSymbol":"PETR4","equitiesQuantity":77},
          {"tickerSymbol":"HGLG11","equitiesQuantity":10}]},
         "Links":{"next":null}}""";
}
