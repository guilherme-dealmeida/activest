package com.primevest.integration.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.primevest.domain.integration.IntegrationProvider;
import com.primevest.domain.integration.credential.ApiKeyCredentials;
import com.primevest.domain.integration.credential.SourceCredentials;
import com.primevest.domain.integration.pipeline.CryptoImportRules;
import com.primevest.domain.integration.pipeline.ImportRules;
import com.primevest.domain.integration.source.CredentialCheck;
import com.primevest.domain.integration.source.FetchResult;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.domain.integration.source.SourceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Conector da Binance: a primeira implementacao de {@link SourceConnector}
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, F3). Serve a qualquer usuario — recebe a
 * credencial da conexao e nao guarda nada entre chamadas.
 *
 * <p>O que busca, e como vira {@link ImportedMovement}:</p>
 * <table>
 *   <tr><th>Fluxo (cursor)</th><th>Endpoint</th><th>Movimento</th></tr>
 *   <tr><td>{@code spot:<PAR>}</td><td>/api/v3/myTrades, por par, paginado por fromId</td>
 *       <td>TRADE — pedacos da mesma ordem somados (R1); id {@code binance:spot:<PAR>:<orderId>}</td></tr>
 *   <tr><td>{@code convert}</td><td>/sapi/v1/convert/tradeFlow, janelas de 30 dias</td>
 *       <td>CONVERT; id {@code binance:convert:<quoteId>}</td></tr>
 *   <tr><td>{@code fiat_payments}</td><td>/sapi/v1/fiat/payments (compra)</td>
 *       <td>TRADE contra BRL, taxa em BRL; id {@code binance:fiatpay:<orderNo>}</td></tr>
 *   <tr><td>{@code fiat_deposits}</td><td>/sapi/v1/fiat/orders (deposito)</td>
 *       <td>FIAT_DEPOSIT; id {@code binance:fiat:<orderNo>}</td></tr>
 *   <tr><td>{@code crypto_deposits}</td><td>/sapi/v1/capital/deposit/hisrec, janelas de 90 dias</td>
 *       <td>CRYPTO_DEPOSIT; id {@code binance:deposit:<id>}</td></tr>
 * </table>
 *
 * <p><b>Quais pares consultar.</b> A Binance so devolve negociacoes par a par.
 * Os candidatos sao as moedas com saldo, as que aparecem nos demais fluxos, as
 * de {@code binance.probe-assets} e os pares ja vistos (cursores), cada uma
 * cruzada com {@code binance.quote-assets} e filtrada pelos pares que existem
 * na corretora. Moeda comprada e vendida por inteiro, fora dessas listas, nao e
 * encontrada — limite registrado no plano.</p>
 *
 * <p>Fora desta versao: saques de cripto, rendimentos (Earn) e venda com
 * "Vender Cripto" ({@code fiat/payments} tipo 1, formato ainda nao observado).</p>
 */
@Slf4j
@Component
public class BinanceConnector implements SourceConnector {

    static final String SPOT = "spot:";
    static final String CONVERT = "convert";
    static final String FIAT_PAYMENTS = "fiat_payments";
    static final String FIAT_DEPOSITS = "fiat_deposits";
    static final String CRYPTO_DEPOSITS = "crypto_deposits";

    private static final long DAY = 86_400_000L;
    private static final int TRADES_PAGE = 1000;
    private static final int FIAT_PAGE = 500;
    private static final int DEPOSIT_PAGE = 1000;

    /** Permissoes que tornam a chave inaceitavel (P2), com o nome que a tela mostra. */
    private static final Map<String, String> FORBIDDEN_PERMISSIONS = new LinkedHashMap<>();

    static {
        FORBIDDEN_PERMISSIONS.put("enableSpotAndMarginTrading", "negociar (spot e margem)");
        FORBIDDEN_PERMISSIONS.put("enableWithdrawals", "sacar");
        FORBIDDEN_PERMISSIONS.put("enableInternalTransfer", "transferir entre contas");
        FORBIDDEN_PERMISSIONS.put("permitsUniversalTransfer", "transferência universal");
        FORBIDDEN_PERMISSIONS.put("enableMargin", "margem");
        FORBIDDEN_PERMISSIONS.put("enableFutures", "futuros");
        FORBIDDEN_PERMISSIONS.put("enableVanillaOptions", "opções");
        FORBIDDEN_PERMISSIONS.put("enablePortfolioMarginTrading", "portfolio margin");
        FORBIDDEN_PERMISSIONS.put("enableFixApiTrade", "negociar via FIX");
    }

    private final BinanceClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long historyStart;
    private final long convertHistoryStart;
    private final long convertPauseMillis;
    private final List<String> quoteAssets;
    private final List<String> probeAssets;

    /** Relogio. Troca nos testes. */
    LongSupplier clock = System::currentTimeMillis;

    public BinanceConnector(BinanceClient client,
                            @Value("${binance.history-start:2017-07-01}") String historyStart,
                            @Value("${binance.convert-history-start:2020-01-01}") String convertHistoryStart,
                            @Value("${binance.convert-pause-millis:1100}") long convertPauseMillis,
                            @Value("${binance.quote-assets:BRL,USDT,USDC,FDUSD,BTC,ETH,BNB}") List<String> quoteAssets,
                            @Value("${binance.probe-assets:BTC,ETH,BNB,SOL,XRP,USDT,USDC,ADA,DOGE}") List<String> probeAssets) {
        this.client = client;
        this.historyStart = epoch(historyStart);
        this.convertHistoryStart = epoch(convertHistoryStart);
        this.convertPauseMillis = convertPauseMillis;
        this.quoteAssets = quoteAssets.stream().map(s -> s.strip().toUpperCase()).toList();
        this.probeAssets = probeAssets.stream().map(s -> s.strip().toUpperCase()).toList();
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.BINANCE;
    }

    @Override
    public ImportRules rules() {
        return CryptoImportRules.INSTANCE;
    }

    // ------------------------------------------------------------------ chave

    @Override
    public CredentialCheck check(SourceCredentials source) {
        ApiKeyCredentials credentials = SourceConnector.as(ApiKeyCredentials.class, source);
        JsonNode r;
        try {
            r = client.signedGet(credentials, "/sapi/v1/account/apiRestrictions", Map.of());
        } catch (BinanceException e) {
            if (e.isCredentialRejected()) {
                return new CredentialCheck(false, false, null,
                    "A Binance recusou a chave. Confira se a chave e o segredo foram copiados inteiros, se a "
                        + "chave não foi apagada e, se ela tiver restrição de IP, se o IP do PrimeVest está liberado.");
            }
            throw e;
        }
        List<String> excess = new ArrayList<>();
        FORBIDDEN_PERMISSIONS.forEach((field, name) -> {
            if (r.path(field).asBoolean(false)) {
                excess.add(name);
            }
        });
        boolean read = r.path("enableReading").asBoolean(false);
        String problem = null;
        if (!excess.isEmpty()) {
            problem = "A chave permite " + String.join(", ", excess)
                + ". Por segurança, o PrimeVest só aceita chave com a permissão de leitura (Enable Reading).";
        } else if (!read) {
            problem = "A chave não tem a permissão de leitura (Enable Reading).";
        }
        return new CredentialCheck(true, read && excess.isEmpty(), r.toString(), problem);
    }

    // ------------------------------------------------------------------ busca

    @Override
    public FetchResult fetch(SourceCredentials source, Map<String, String> cursors) {
        ApiKeyCredentials credentials = SourceConnector.as(ApiKeyCredentials.class, source);
        long now = clock.getAsLong();
        Map<String, String> nextCursors = new HashMap<>(cursors);
        List<ImportedMovement> movements = new ArrayList<>();

        movements.addAll(fiatDeposits(credentials, cursors, nextCursors, now));
        movements.addAll(fiatPayments(credentials, cursors, nextCursors, now));
        movements.addAll(converts(credentials, cursors, nextCursors, now));
        movements.addAll(cryptoDeposits(credentials, cursors, nextCursors, now));

        // Saldo da conta: serve para escolher os pares a consultar e para o
        // aviso de divergencia (E3). So a carteira Spot — Earn e Funding ficam de fora.
        JsonNode account = client.signedGet(credentials, "/api/v3/account", Map.of("omitZeroBalances", true));
        Map<String, BigDecimal> balances = new TreeMap<>();
        account.path("balances").forEach(b -> {
            BigDecimal total = zero(dec(b.path("free"))).add(zero(dec(b.path("locked"))));
            if (total.signum() != 0) {
                balances.merge(b.path("asset").asText().toUpperCase(), total, BigDecimal::add);
            }
        });
        movements.addAll(spotTrades(credentials, cursors, nextCursors, movements, balances.keySet()));

        return new FetchResult(movements, nextCursors, balances);
    }

    List<ImportedMovement> fiatDeposits(ApiKeyCredentials c, Map<String, String> cursors,
                                        Map<String, String> nextCursors, long now) {
        long start = start(cursors, FIAT_DEPOSITS, historyStart);
        List<ImportedMovement> out = new ArrayList<>();
        for (JsonNode o : fiatPages(c, "/sapi/v1/fiat/orders", start, now)) {
            if (!"Successful".equalsIgnoreCase(o.path("status").asText())) {
                continue;
            }
            long t = o.path("createTime").asLong();
            out.add(new ImportedMovement("binance:fiat:" + o.path("orderNo").asText(), MovementKind.FIAT_DEPOSIT,
                o.path("fiatCurrency").asText(), null, null, o.path("fiatCurrency").asText(),
                dec(o.path("amount")), dec(o.path("totalFee")), o.path("fiatCurrency").asText(),
                Instant.ofEpochMilli(t), o.toString()));
            advance(nextCursors, FIAT_DEPOSITS, t);
        }
        return out;
    }

    List<ImportedMovement> fiatPayments(ApiKeyCredentials c, Map<String, String> cursors,
                                        Map<String, String> nextCursors, long now) {
        long start = start(cursors, FIAT_PAYMENTS, historyStart);
        List<ImportedMovement> out = new ArrayList<>();
        for (JsonNode p : fiatPages(c, "/sapi/v1/fiat/payments", start, now)) {
            String status = p.path("status").asText();
            if (!"Completed".equalsIgnoreCase(status) && !"Successful".equalsIgnoreCase(status)) {
                continue;
            }
            long t = p.path("createTime").asLong();
            BigDecimal sourceName = dec(p.path("sourceAmount"));
            BigDecimal fee = dec(p.path("totalFee"));
            // quoteAmount SEM a taxa, que e cobrada na propria moeda de cotacao (6.14).
            BigDecimal withoutFee = sourceName == null ? null : sourceName.subtract(fee == null ? BigDecimal.ZERO : fee);
            out.add(new ImportedMovement("binance:fiatpay:" + p.path("orderNo").asText(), MovementKind.TRADE,
                p.path("cryptoCurrency").asText(), MovementSide.BUY, dec(p.path("obtainAmount")),
                p.path("fiatCurrency").asText(), withoutFee, fee, p.path("fiatCurrency").asText(),
                Instant.ofEpochMilli(t), p.toString()));
            advance(nextCursors, FIAT_PAYMENTS, t);
        }
        return out;
    }

    /** Endpoints de fiat: uma janela so, paginada. transactionType 0 = deposito / compra. */
    private List<JsonNode> fiatPages(ApiKeyCredentials c, String path, long start, long end) {
        List<JsonNode> all = new ArrayList<>();
        if (start > end) {
            return all;
        }
        for (int page = 1; ; page++) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("transactionType", 0);
            params.put("beginTime", start);
            params.put("endTime", end);
            params.put("page", page);
            params.put("rows", FIAT_PAGE);
            JsonNode data = client.signedGet(c, path, params).path("data");
            data.forEach(all::add);
            if (data.size() < FIAT_PAGE) {
                return all;
            }
        }
    }

    List<ImportedMovement> converts(ApiKeyCredentials c, Map<String, String> cursors,
                                    Map<String, String> nextCursors, long now) {
        long start = start(cursors, CONVERT, convertHistoryStart);
        List<ImportedMovement> out = new ArrayList<>();
        boolean first = true;
        for (long[] w : windows(start, now, 30)) {
            if (!first) {
                client.pause(convertPauseMillis);
            }
            first = false;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("startTime", w[0]);
            params.put("endTime", w[1]);
            params.put("limit", 1000);
            JsonNode r = client.signedGet(c, "/sapi/v1/convert/tradeFlow", params);
            if (r.path("moreData").asBoolean(false)) {
                log.warn("Convert: mais de 1000 conversoes em uma janela de 30 dias; o excedente fica para a proxima busca");
            }
            for (JsonNode t : r.path("list")) {
                if (!"SUCCESS".equalsIgnoreCase(t.path("orderStatus").asText())) {
                    continue;
                }
                out.add(convert(t));
                advance(nextCursors, CONVERT, t.path("createTime").asLong());
            }
        }
        return out;
    }

    /**
     * BRL -> X e compra de X; X -> BRL e venda de X. Entre duas cripto, o
     * movimento sai como compra do destino cotada na origem — e o pipeline o
     * deixa de fora por nao ter valor em reais (R6).
     */
    ImportedMovement convert(JsonNode t) {
        String de = t.path("fromAsset").asText();
        String to = t.path("toAsset").asText();
        BigDecimal qtdDe = dec(t.path("fromAmount"));
        BigDecimal quantityFor = dec(t.path("toAmount"));
        boolean sale = "BRL".equalsIgnoreCase(to);
        return new ImportedMovement("binance:convert:" + t.path("quoteId").asText(), MovementKind.CONVERT,
            sale ? de : to,
            sale ? MovementSide.SELL : MovementSide.BUY,
            sale ? qtdDe : quantityFor,
            sale ? to : de,
            sale ? quantityFor : qtdDe,
            null, null, Instant.ofEpochMilli(t.path("createTime").asLong()), t.toString());
    }

    List<ImportedMovement> cryptoDeposits(ApiKeyCredentials c, Map<String, String> cursors,
                                          Map<String, String> nextCursors, long now) {
        long start = start(cursors, CRYPTO_DEPOSITS, historyStart);
        List<ImportedMovement> out = new ArrayList<>();
        for (long[] w : windows(start, now, 90)) {
            for (int offset = 0; ; offset += DEPOSIT_PAGE) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("status", 1);
                params.put("startTime", w[0]);
                params.put("endTime", w[1]);
                params.put("offset", offset);
                params.put("limit", DEPOSIT_PAGE);
                JsonNode page = client.signedGet(c, "/sapi/v1/capital/deposit/hisrec", params);
                for (JsonNode d : page) {
                    long t = d.path("insertTime").asLong();
                    String id = d.hasNonNull("id") ? d.path("id").asText() : d.path("txId").asText();
                    out.add(new ImportedMovement("binance:deposit:" + id, MovementKind.CRYPTO_DEPOSIT,
                        d.path("coin").asText(), MovementSide.BUY, dec(d.path("amount")), null, null, null, null,
                        Instant.ofEpochMilli(t), d.toString()));
                    advance(nextCursors, CRYPTO_DEPOSITS, t);
                }
                if (page.size() < DEPOSIT_PAGE) {
                    break;
                }
            }
        }
        return out;
    }

    List<ImportedMovement> spotTrades(ApiKeyCredentials c, Map<String, String> cursors,
                                      Map<String, String> nextCursors, List<ImportedMovement> others,
                                      Set<String> withBalance) {
        // Moedas candidatas: saldo atual, as dos outros fluxos, a lista fixa e os pares ja vistos.
        Set<String> bases = new LinkedHashSet<>(probeAssets);
        bases.addAll(withBalance);
        others.forEach(m -> {
            if (m.asset() != null && m.kind() != MovementKind.FIAT_DEPOSIT) {
                bases.add(m.asset().toUpperCase());
            }
        });
        bases.remove("BRL");

        Map<String, String[]> pairs = new HashMap<>();
        client.publicGet("/api/v3/exchangeInfo", Map.of()).path("symbols").forEach(s ->
            pairs.put(s.path("symbol").asText(), new String[] {s.path("baseAsset").asText(), s.path("quoteAsset").asText()}));

        Set<String> symbols = new LinkedHashSet<>();
        for (String base : bases) {
            for (String quote : quoteAssets) {
                if (!base.equals(quote) && pairs.containsKey(base + quote)) {
                    symbols.add(base + quote);
                }
            }
        }
        cursors.keySet().stream().filter(k -> k.startsWith(SPOT)).map(k -> k.substring(SPOT.length()))
            .filter(pairs::containsKey).forEach(symbols::add);

        List<ImportedMovement> out = new ArrayList<>();
        for (String symbol : symbols) {
            String[] par = pairs.get(symbol);
            List<JsonNode> fills = trades(c, symbol, cursors.get(SPOT + symbol));
            if (fills.isEmpty()) {
                continue;
            }
            // R1: os pedacos da mesma ordem viram um movimento.
            Map<String, List<JsonNode>> byOrder = new TreeMap<>(Comparator.comparingLong(Long::parseLong));
            for (JsonNode f : fills) {
                byOrder.computeIfAbsent(f.path("orderId").asText(), k -> new ArrayList<>()).add(f);
            }
            byOrder.forEach((orderId, chunks) -> out.add(order(symbol, par[0], par[1], orderId, chunks)));
            long last = fills.stream().mapToLong(f -> f.path("id").asLong()).max().orElseThrow();
            nextCursors.put(SPOT + symbol, Long.toString(last));
        }
        return out;
    }

    private List<JsonNode> trades(ApiKeyCredentials c, String symbol, String cursor) {
        List<JsonNode> all = new ArrayList<>();
        long fromId = cursor == null ? 0 : Long.parseLong(cursor) + 1;
        while (true) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("fromId", fromId);
            params.put("limit", TRADES_PAGE);
            JsonNode page = client.signedGet(c, "/api/v3/myTrades", params);
            page.forEach(all::add);
            if (page.size() < TRADES_PAGE) {
                return all;
            }
            fromId = page.get(page.size() - 1).path("id").asLong() + 1;
        }
    }

    ImportedMovement order(String symbol, String base, String quote, String orderId, List<JsonNode> chunks) {
        BigDecimal qtd = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        Map<String, BigDecimal> fees = new TreeMap<>();
        long start = Long.MAX_VALUE;
        for (JsonNode f : chunks) {
            qtd = qtd.add(dec(f.path("qty")));
            value = value.add(dec(f.path("quoteQty")));
            BigDecimal commission = dec(f.path("commission"));
            if (commission != null && commission.signum() != 0) {
                fees.merge(f.path("commissionAsset").asText().toUpperCase(), commission, BigDecimal::add);
            }
            start = Math.min(start, f.path("time").asLong());
        }
        // Taxa em mais de uma moeda na mesma ordem: sai como "BNB+BTC" e o
        // pipeline a deixa de fora pela R8, com o motivo.
        String feeCurrency = fees.isEmpty() ? null : String.join("+", fees.keySet());
        BigDecimal fee = fees.values().stream().reduce(BigDecimal::add).orElse(null);

        ObjectNode raw = mapper.createObjectNode();
        raw.put("symbol", symbol);
        raw.put("orderId", orderId);
        ArrayNode arr = raw.putArray("fills");
        chunks.forEach(arr::add);

        return new ImportedMovement("binance:spot:" + symbol + ":" + orderId, MovementKind.TRADE,
            base, chunks.get(0).path("isBuyer").asBoolean() ? MovementSide.BUY : MovementSide.SELL,
            qtd, quote, value, fee, feeCurrency, Instant.ofEpochMilli(start), raw.toString());
    }

    // ------------------------------------------------------------------ apoio

    private static long start(Map<String, String> cursors, String stream, long isDefault) {
        String c = cursors.get(stream);
        return c == null ? isDefault : Long.parseLong(c) + 1;
    }

    private static void advance(Map<String, String> nextCursors, String stream, long instant) {
        nextCursors.merge(stream, Long.toString(instant),
            (a, b) -> Long.parseLong(a) >= Long.parseLong(b) ? a : b);
    }

    static List<long[]> windows(long start, long end, int days) {
        List<long[]> out = new ArrayList<>();
        for (long a = start; a <= end; a += days * DAY) {
            out.add(new long[] {a, Math.min(a + days * DAY - 1, end)});
        }
        return out;
    }

    private static long epoch(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal dec(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull() || n.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(n.asText());
    }
}
