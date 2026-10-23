package com.primevest.integration.b3investidor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.primevest.domain.integration.IntegrationProvider;
import com.primevest.domain.integration.credential.InvestorDocument;
import com.primevest.domain.integration.credential.SourceCredentials;
import com.primevest.domain.integration.pipeline.ImportRules;
import com.primevest.domain.integration.source.CredentialCheck;
import com.primevest.domain.integration.source.FetchResult;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.domain.integration.source.SourceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.primevest.integration.b3investidor.B3EquityImportRules.baseTicker;
import static com.primevest.integration.b3investidor.B3EquityImportRules.normalize;

/**
 * Conector da API da Area do Investidor da B3 (docs/planos/PLANO_INTEGRACAO_B3.md, 6.1 a 6.9).
 * A credencial e o CPF do investidor ({@link InvestorDocument}); quem autoriza o acesso e ele,
 * na B3 (opt-in). Serve a qualquer usuario e nao guarda nada entre chamadas.
 *
 * <table>
 *   <tr><th>Fluxo (cursor)</th><th>API</th><th>Movimento</th></tr>
 *   <tr><td>{@code negociacao} (ultima data buscada)</td><td>Negociacao de Ativos</td>
 *       <td>EQUITY_TRADE — negocios do dia, ativo (fracionario junto) e lado somados (B1);
 *       id {@code b3:negociacao:<data>:<TICKER>:<C|V>} (B5)</td></tr>
 *   <tr><td>{@code movimentacao} (ultima data buscada)</td><td>Movimentacao: acoes, Tesouro, renda fixa</td>
 *       <td>SETTLEMENT, CASH_EVENT, STOCK_EVENT, RIGHTS, CUSTODY_TRANSFER, LENDING, FIXED_INCOME, OTHER —
 *       conferencia; id {@code b3:movimentacao:...}</td></tr>
 *   <tr><td>— (foto do dia)</td><td>Eventos Provisionados</td><td>CASH_EVENT a receber — conferencia</td></tr>
 *   <tr><td>— (foto do dia)</td><td>Posicao (acoes)</td><td>saldos para a divergencia (6.7)</td></tr>
 * </table>
 *
 * <p><b>Periodo</b>: a B3 publica o dia anterior (D-1) a partir das 8h de Brasilia (A6). A busca vai do
 * cursor (ou do inicio do historico da B3, A5) ate o ultimo dia publicado. Rota de conferencia
 * ({@code b3i.paths.position}, {@code b3i.paths.provisioned-events}) em branco = nao consultada.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "b3i", name = "enabled", havingValue = "true")
public class B3InvestidorConnector implements SourceConnector {

    static final String TRADES_STREAM = "negociacao";
    static final String MOVEMENTS_STREAM = "movimentacao";
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    static final List<String> PRODUCTS = List.of("equities", "treasury-bonds", "fixed-income");
    private static final int MAX_ID = 200;

    static final String AWAITING_AUTHORIZATION = "Falta autorizar o PrimeVest na Área do Investidor da B3. Abra o link de "
        + "autorização, entre com o seu login da B3, autorize e sincronize de novo.";

    private final B3InvestidorClient client;
    private final B3InvestidorProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Relogio. Troca nos testes. */
    Clock clock = Clock.system(SAO_PAULO);

    public B3InvestidorConnector(B3InvestidorClient client, B3InvestidorProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.B3;
    }

    @Override
    public ImportRules rules() {
        return B3EquityImportRules.INSTANCE;
    }

    @Override
    public boolean oncePerDay() {
        return props.isOncePerDay();
    }

    @Override
    public String authorizationUrl() {
        return blank(props.getOptinUrl()) ? null : props.getOptinUrl();
    }

    // ------------------------------------------------------------------ autorizacao

    /**
     * A credencial e o CPF: "aceita" quando a B3 o reconhece; "so leitura" sempre (a API nao tem
     * escrita); a autorizacao do investidor e o que falta ou nao (G8).
     */
    @Override
    public CredentialCheck check(SourceCredentials source) {
        InvestorDocument doc = SourceConnector.as(InvestorDocument.class, source);
        JsonNode r;
        try {
            r = client.get(props.getPaths().getAuthorization(), vars(doc, null), Map.of());
        } catch (B3InvestidorException e) {
            if (e.is(B3InvestidorException.NOT_FOUND)) {
                return new CredentialCheck(false, false, null,
                    "A B3 não localizou este CPF entre os investidores. Confira o número.");
            }
            if (e.is(B3InvestidorException.INVALID_DOCUMENT)) {
                return new CredentialCheck(false, false, null, "A B3 não aceitou o formato deste CPF.");
            }
            if (e.is(B3InvestidorException.NOT_AUTHORIZED)) {
                return CredentialCheck.pendingAuthorization(AWAITING_AUTHORIZATION);
            }
            throw e;
        }
        JsonNode data = r.has("data") ? r.path("data") : r;
        if (!data.path("authorized").asBoolean(false)) {
            return CredentialCheck.pendingAuthorization(AWAITING_AUTHORIZATION);
        }
        ObjectNode permissions = mapper.createObjectNode();
        permissions.put("authorized", true);
        permissions.put("authorizationDate", text(data, "authorizationDate"));
        return new CredentialCheck(true, true, permissions.toString(), null);
    }

    // ------------------------------------------------------------------ busca

    @Override
    public FetchResult fetch(SourceCredentials source, Map<String, String> cursors) {
        InvestorDocument doc = SourceConnector.as(InvestorDocument.class, source);
        LocalDate end = lastPublishedDay();
        Map<String, String> nextCursors = new HashMap<>(cursors);
        List<ImportedMovement> movements = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();

        LocalDate tradesStart = start(cursors, TRADES_STREAM);
        if (!tradesStart.isAfter(end)) {
            movements.addAll(tradeRows(doc, tradesStart, end));
            nextCursors.put(TRADES_STREAM, end.toString());
        }
        LocalDate movementsStart = start(cursors, MOVEMENTS_STREAM);
        if (!movementsStart.isAfter(end)) {
            movements.addAll(movementRows(doc, movementsStart, end, ids));
            nextCursors.put(MOVEMENTS_STREAM, end.toString());
        }
        movements.addAll(dividendsToReceive(doc, end, ids));
        return new FetchResult(movements, nextCursors, position(doc, end));
    }

    /** D-1; antes do horario de publicacao da B3, D-2 (A6). */
    LocalDate lastPublishedDay() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SAO_PAULO);
        return now.toLocalTime().isBefore(props.getAvailableAfter())
            ? now.toLocalDate().minusDays(2) : now.toLocalDate().minusDays(1);
    }

    private LocalDate start(Map<String, String> cursors, String stream) {
        String c = cursors.get(stream);
        return c == null ? props.getHistoryStart() : LocalDate.parse(c).plusDays(1);
    }

    // --- negociacao (B1, B5)

    List<ImportedMovement> tradeRows(InvestorDocument doc, LocalDate start, LocalDate end) {
        // Chave do grupo: data, ticker sem o F do fracionario, lado. TreeMap: ordem estavel.
        Map<String, List<ObjectNode>> groups = new TreeMap<>();
        for (JsonNode page : client.getAll(props.getPaths().getAssetTrading(), vars(doc, null), period(start, end))) {
            for (JsonNode day : page.path("data").path("periodList")) {
                String data = text(day, "referenceDate");
                for (JsonNode trade : day.path("assetTradingList")) {
                    String ticker = text(trade, "tickerSymbol");
                    if (data == null || ticker == null) {
                        log.warn("B3: negócio sem data ou sem código ignorado");
                        continue;
                    }
                    ObjectNode n = trade.deepCopy();
                    n.put("referenceDate", data);
                    MovementSide side = side(text(trade, "side"));
                    String key = data + ":" + baseTicker(ticker) + ":" + letter(side);
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(n);
                }
            }
        }
        List<ImportedMovement> out = new ArrayList<>();
        groups.forEach((key, trades) -> out.add(group(key, trades)));
        return out;
    }

    ImportedMovement group(String key, List<ObjectNode> trades) {
        ObjectNode firstValue = trades.get(0);
        LocalDate data = LocalDate.parse(firstValue.path("referenceDate").asText());
        String ticker = baseTicker(firstValue.path("tickerSymbol").asText());
        MovementSide side = side(text(firstValue, "side"));

        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        String market = null;
        Set<String> institutions = new LinkedHashSet<>();
        for (ObjectNode n : trades) {
            BigDecimal q = dec(n.path("tradeQuantity"));
            BigDecimal v = dec(n.path("totalTradeAmount"));
            BigDecimal p = dec(n.path("priceValue"));
            if (v == null && q != null && p != null) {
                v = q.multiply(p); // a API v1 traz o total; sem ele, quantidade x preco da propria API
            }
            quantity = quantity == null || q == null ? null : quantity.add(q);
            value = value == null || v == null ? null : value.add(v);
            String m = text(n, "marketName");
            if (market == null || (m != null && !B3EquityImportRules.SPOT_MARKETS.contains(normalize(m)))) {
                market = m; // um negocio fora do a vista marca o grupo (B6)
            }
            String i = text(n, "participantName");
            if (i != null) {
                institutions.add(i);
            }
        }

        ObjectNode raw = mapper.createObjectNode();
        raw.put("referenceDate", data.toString());
        raw.put("tickerSymbol", ticker);
        raw.put("side", text(firstValue, "side"));
        ArrayNode arr = raw.putArray("trades");
        trades.forEach(arr::add);

        return new ImportedMovement("b3:negociacao:" + key, MovementKind.EQUITY_TRADE, ticker, side,
            quantity, "BRL", value, null, null, startOfDay(data), raw.toString(),
            market, institutions.isEmpty() ? null : String.join(" / ", institutions), data);
    }

    // --- movimentacao (conferencia; F7 para Tesouro e renda fixa)

    List<ImportedMovement> movementRows(InvestorDocument doc, LocalDate start, LocalDate end, Set<String> ids) {
        List<ImportedMovement> out = new ArrayList<>();
        for (String product : PRODUCTS) {
            for (JsonNode page : client.getAll(props.getPaths().getMovement(), vars(doc, product), period(start, end))) {
                for (JsonNode m : list(page.path("data"), "Movements")) {
                    ImportedMovement mov = movement(product, m, ids);
                    if (mov != null) {
                        out.add(mov);
                    }
                }
            }
        }
        return out;
    }

    ImportedMovement movement(String product, JsonNode m, Set<String> ids) {
        String data = text(m, "referenceDate");
        if (data == null) {
            log.warn("B3: movimentação sem data ignorada");
            return null;
        }
        String type = text(m, "movementType");
        boolean shares = "equities".equals(product);
        MovementKind kind = shares ? classify(type) : MovementKind.FIXED_INCOME;
        String asset = shares ? text(m, "tickerSymbol") : first(m, "assetDescription", "tickerSymbol");
        MovementSide side = side(text(m, "operationType"));
        BigDecimal quantity = dec(firstNode(m, "equitiesQuantity", "treasuryBondsQuantity", "fixedIncomeQuantity", "quantity"));
        BigDecimal value = dec(m.path("operationValue"));
        LocalDate day = LocalDate.parse(data);

        String id = single(ids, "b3:movimentacao:" + data + ":" + (asset == null ? "-" : asset) + ":" + slug(type)
            + ":" + (side == MovementSide.SELL ? "D" : "C") + ":" + plain(quantity) + ":" + plain(value));
        return new ImportedMovement(id, kind, asset, side, quantity, "BRL", value, null, null,
            startOfDay(day), m.toString(), type, text(m, "participantName"), day);
    }

    /** O tipo da Movimentacao da B3 no MovementKind (6.4, 6.6). Sem regra = OTHER, com o tipo no motivo. */
    static MovementKind classify(String type) {
        if (type == null) {
            return MovementKind.OTHER;
        }
        String t = normalize(type);
        if (t.contains("LIQUIDACAO")) {
            return MovementKind.SETTLEMENT;
        }
        if (t.contains("EMPRESTIMO")) {
            return MovementKind.LENDING;
        }
        if (t.contains("DIREITO") || t.contains("CESSAO") || t.contains("SUBSCRICAO") || t.contains("RECIBO")) {
            return MovementKind.RIGHTS;
        }
        if (t.contains("DIVIDENDO") || t.contains("JUROS") || t.contains("RENDIMENTO")
            || t.contains("AMORTIZACAO") || t.contains("RESTITUICAO")) {
            return MovementKind.CASH_EVENT;
        }
        if (t.contains("ATUALIZACAO") || t.contains("BONIFICACAO") || t.contains("DESDOBR") || t.contains("GRUPAMENTO")
            || t.contains("FRACAO") || t.contains("INCORPORACAO") || t.contains("CISAO") || t.contains("CONVERSAO")) {
            return MovementKind.STOCK_EVENT;
        }
        if (t.startsWith("TRANSFERENCIA")) {
            return MovementKind.CUSTODY_TRANSFER;
        }
        return MovementKind.OTHER;
    }

    // --- eventos provisionados (proventos a receber; conferencia)

    List<ImportedMovement> dividendsToReceive(InvestorDocument doc, LocalDate end, Set<String> ids) {
        String route = props.getPaths().getProvisionedEvents();
        if (blank(route)) {
            return List.of();
        }
        List<ImportedMovement> out = new ArrayList<>();
        for (JsonNode page : client.getAll(route, vars(doc, null), period(end, end))) {
            for (JsonNode e : list(page.path("data"), "Events")) {
                String ticker = text(e, "tickerSymbol");
                String type = text(e, "corporateActionTypeDescription");
                String payment = first(e, "PaymentDate", "paymentDate");
                LocalDate day = payment == null ? null : LocalDate.parse(payment);
                BigDecimal quantity = dec(e.path("eventQuantity"));
                BigDecimal value = dec(firstNode(e, "netValue", "grossValue"));
                String id = single(ids, "b3:provisionado:" + (payment == null ? "-" : payment) + ":"
                    + (ticker == null ? "-" : ticker) + ":" + slug(type) + ":" + plain(quantity) + ":" + plain(value));
                out.add(new ImportedMovement(id, MovementKind.CASH_EVENT, ticker, MovementSide.BUY, quantity, "BRL",
                    value, null, null, startOfDay(day != null ? day : end), e.toString(),
                    (type == null ? "Provento" : type) + " a receber", text(e, "participantName"), day));
            }
        }
        return out;
    }

    // --- posicao (divergencia, 6.7)

    Map<String, BigDecimal> position(InvestorDocument doc, LocalDate end) {
        String route = props.getPaths().getPosition();
        Map<String, BigDecimal> balances = new TreeMap<>();
        if (blank(route)) {
            return balances;
        }
        for (JsonNode page : client.getAll(route, vars(doc, "equities"), period(end, end))) {
            for (JsonNode p : list(page.path("data"), "Positions")) {
                String ticker = text(p, "tickerSymbol");
                BigDecimal q = dec(firstNode(p, "equitiesQuantity", "quantity"));
                if (ticker != null && q != null && q.signum() != 0) {
                    balances.merge(baseTicker(ticker), q, BigDecimal::add);
                }
            }
        }
        return balances;
    }

    // ------------------------------------------------------------------ apoio

    private static Map<String, String> vars(InvestorDocument doc, String product) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("documentNumber", doc.documentNumber());
        if (product != null) {
            v.put("product", product);
        }
        return v;
    }

    private static Map<String, Object> period(LocalDate start, LocalDate end) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("referenceStartDate", start.toString());
        q.put("referenceEndDate", end.toString());
        return q;
    }

    /**
     * A lista do corpo, tolerante ao aninhamento (A3): a primeira matriz cujo nome termina com o
     * sufixo, em {@code data} ou um nivel abaixo ({@code data.equitiesPeriods.equitiesMovements}).
     */
    static List<JsonNode> list(JsonNode data, String suffix) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode found = search(data, suffix, 0);
        if (found != null) {
            found.forEach(out::add);
        }
        return out;
    }

    private static JsonNode search(JsonNode jsonNode, String suffix, int level) {
        if (jsonNode == null || !jsonNode.isObject() || level > 2) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> fieldNames = jsonNode.fields();
        List<JsonNode> children = new ArrayList<>();
        while (fieldNames.hasNext()) {
            Map.Entry<String, JsonNode> c = fieldNames.next();
            if (c.getValue().isArray() && c.getKey().toLowerCase().endsWith(suffix.toLowerCase())) {
                return c.getValue();
            }
            if (c.getValue().isObject()) {
                children.add(c.getValue());
            }
        }
        for (JsonNode f : children) {
            JsonNode r = search(f, suffix, level + 1);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /** Compra/Venda, Credito/Debito, Entrada/Saida (com ou sem acento). */
    static MovementSide side(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = normalize(s);
        if (t.startsWith("C") || t.startsWith("B") || t.startsWith("ENTRADA")) {
            return MovementSide.BUY;
        }
        if (t.startsWith("V") || t.startsWith("D") || t.startsWith("S")) {
            return MovementSide.SELL;
        }
        return null;
    }

    private static String letter(MovementSide side) {
        return side == null ? "?" : side == MovementSide.BUY ? "C" : "V";
    }

    private static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(SAO_PAULO).toInstant();
    }

    /** Id unico na busca (dois lancamentos iguais no mesmo dia ganham #2, #3) e dentro do limite da coluna. */
    private static String single(Set<String> ids, String base) {
        String id = base.length() > MAX_ID ? base.substring(0, MAX_ID - 9) + ":" + Integer.toHexString(base.hashCode()) : base;
        String candidate = id;
        for (int n = 2; !ids.add(candidate); n++) {
            candidate = id + "#" + n;
        }
        return candidate;
    }

    private static String slug(String s) {
        return s == null ? "-" : normalize(s).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String plain(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText().strip();
    }

    private static String first(JsonNode n, String... fieldNames) {
        for (String c : fieldNames) {
            String v = text(n, c);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode n, String... fieldNames) {
        for (String c : fieldNames) {
            JsonNode v = n.path(c);
            if (!v.isMissingNode() && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    /** Numero da B3: numero JSON, ou texto com virgula decimal ("1.479,25", "0,25"); "-" ou vazio = nulo. */
    static BigDecimal dec(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        String t = n.asText().strip();
        if (t.isEmpty() || "-".equals(t)) {
            return null;
        }
        try {
            return new BigDecimal(t.contains(",") ? t.replace(".", "").replace(",", ".") : t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
