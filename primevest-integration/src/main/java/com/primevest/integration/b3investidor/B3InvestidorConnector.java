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

    static final String NEGOCIACAO = "negociacao";
    static final String MOVIMENTACAO = "movimentacao";
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    static final List<String> PRODUTOS = List.of("equities", "treasury-bonds", "fixed-income");
    private static final int MAX_ID = 200;

    static final String AGUARDANDO = "Falta autorizar o PrimeVest na Área do Investidor da B3. Abra o link de "
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
                return CredentialCheck.pendingAuthorization(AGUARDANDO);
            }
            throw e;
        }
        JsonNode data = r.has("data") ? r.path("data") : r;
        if (!data.path("authorized").asBoolean(false)) {
            return CredentialCheck.pendingAuthorization(AGUARDANDO);
        }
        ObjectNode permissoes = mapper.createObjectNode();
        permissoes.put("authorized", true);
        permissoes.put("authorizationDate", text(data, "authorizationDate"));
        return new CredentialCheck(true, true, permissoes.toString(), null);
    }

    // ------------------------------------------------------------------ busca

    @Override
    public FetchResult fetch(SourceCredentials source, Map<String, String> cursors) {
        InvestorDocument doc = SourceConnector.as(InvestorDocument.class, source);
        LocalDate fim = ultimoDiaPublicado();
        Map<String, String> proximos = new HashMap<>(cursors);
        List<ImportedMovement> movimentos = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();

        LocalDate inicioNegociacao = inicio(cursors, NEGOCIACAO);
        if (!inicioNegociacao.isAfter(fim)) {
            movimentos.addAll(negociacoes(doc, inicioNegociacao, fim));
            proximos.put(NEGOCIACAO, fim.toString());
        }
        LocalDate inicioMovimentacao = inicio(cursors, MOVIMENTACAO);
        if (!inicioMovimentacao.isAfter(fim)) {
            movimentos.addAll(movimentacoes(doc, inicioMovimentacao, fim, ids));
            proximos.put(MOVIMENTACAO, fim.toString());
        }
        movimentos.addAll(proventosAReceber(doc, fim, ids));
        return new FetchResult(movimentos, proximos, posicao(doc, fim));
    }

    /** D-1; antes do horario de publicacao da B3, D-2 (A6). */
    LocalDate ultimoDiaPublicado() {
        ZonedDateTime agora = ZonedDateTime.now(clock).withZoneSameInstant(SAO_PAULO);
        return agora.toLocalTime().isBefore(props.getAvailableAfter())
            ? agora.toLocalDate().minusDays(2) : agora.toLocalDate().minusDays(1);
    }

    private LocalDate inicio(Map<String, String> cursors, String stream) {
        String c = cursors.get(stream);
        return c == null ? props.getHistoryStart() : LocalDate.parse(c).plusDays(1);
    }

    // --- negociacao (B1, B5)

    List<ImportedMovement> negociacoes(InvestorDocument doc, LocalDate inicio, LocalDate fim) {
        // Chave do grupo: data, ticker sem o F do fracionario, lado. TreeMap: ordem estavel.
        Map<String, List<ObjectNode>> grupos = new TreeMap<>();
        for (JsonNode pagina : client.getAll(props.getPaths().getAssetTrading(), vars(doc, null), periodo(inicio, fim))) {
            for (JsonNode dia : pagina.path("data").path("periodList")) {
                String data = text(dia, "referenceDate");
                for (JsonNode negocio : dia.path("assetTradingList")) {
                    String ticker = text(negocio, "tickerSymbol");
                    if (data == null || ticker == null) {
                        log.warn("B3: negócio sem data ou sem código ignorado");
                        continue;
                    }
                    ObjectNode n = negocio.deepCopy();
                    n.put("referenceDate", data);
                    MovementSide lado = lado(text(negocio, "side"));
                    String chave = data + ":" + baseTicker(ticker) + ":" + letra(lado);
                    grupos.computeIfAbsent(chave, k -> new ArrayList<>()).add(n);
                }
            }
        }
        List<ImportedMovement> out = new ArrayList<>();
        grupos.forEach((chave, negocios) -> out.add(grupo(chave, negocios)));
        return out;
    }

    ImportedMovement grupo(String chave, List<ObjectNode> negocios) {
        ObjectNode primeiro = negocios.get(0);
        LocalDate data = LocalDate.parse(primeiro.path("referenceDate").asText());
        String ticker = baseTicker(primeiro.path("tickerSymbol").asText());
        MovementSide lado = lado(text(primeiro, "side"));

        BigDecimal quantidade = BigDecimal.ZERO;
        BigDecimal valor = BigDecimal.ZERO;
        String mercado = null;
        Set<String> instituicoes = new LinkedHashSet<>();
        for (ObjectNode n : negocios) {
            BigDecimal q = dec(n.path("tradeQuantity"));
            BigDecimal v = dec(n.path("totalTradeAmount"));
            BigDecimal p = dec(n.path("priceValue"));
            if (v == null && q != null && p != null) {
                v = q.multiply(p); // a API v1 traz o total; sem ele, quantidade x preco da propria API
            }
            quantidade = quantidade == null || q == null ? null : quantidade.add(q);
            valor = valor == null || v == null ? null : valor.add(v);
            String m = text(n, "marketName");
            if (mercado == null || (m != null && !B3EquityImportRules.SPOT_MARKETS.contains(normalize(m)))) {
                mercado = m; // um negocio fora do a vista marca o grupo (B6)
            }
            String i = text(n, "participantName");
            if (i != null) {
                instituicoes.add(i);
            }
        }

        ObjectNode raw = mapper.createObjectNode();
        raw.put("referenceDate", data.toString());
        raw.put("tickerSymbol", ticker);
        raw.put("side", text(primeiro, "side"));
        ArrayNode arr = raw.putArray("trades");
        negocios.forEach(arr::add);

        return new ImportedMovement("b3:negociacao:" + chave, MovementKind.EQUITY_TRADE, ticker, lado,
            quantidade, "BRL", valor, null, null, inicioDoDia(data), raw.toString(),
            mercado, instituicoes.isEmpty() ? null : String.join(" / ", instituicoes), data);
    }

    // --- movimentacao (conferencia; F7 para Tesouro e renda fixa)

    List<ImportedMovement> movimentacoes(InvestorDocument doc, LocalDate inicio, LocalDate fim, Set<String> ids) {
        List<ImportedMovement> out = new ArrayList<>();
        for (String produto : PRODUTOS) {
            for (JsonNode pagina : client.getAll(props.getPaths().getMovement(), vars(doc, produto), periodo(inicio, fim))) {
                for (JsonNode m : lista(pagina.path("data"), "Movements")) {
                    ImportedMovement mov = movimento(produto, m, ids);
                    if (mov != null) {
                        out.add(mov);
                    }
                }
            }
        }
        return out;
    }

    ImportedMovement movimento(String produto, JsonNode m, Set<String> ids) {
        String data = text(m, "referenceDate");
        if (data == null) {
            log.warn("B3: movimentação sem data ignorada");
            return null;
        }
        String tipo = text(m, "movementType");
        boolean acoes = "equities".equals(produto);
        MovementKind kind = acoes ? classificar(tipo) : MovementKind.FIXED_INCOME;
        String ativo = acoes ? text(m, "tickerSymbol") : first(m, "assetDescription", "tickerSymbol");
        MovementSide lado = lado(text(m, "operationType"));
        BigDecimal quantidade = dec(firstNode(m, "equitiesQuantity", "treasuryBondsQuantity", "fixedIncomeQuantity", "quantity"));
        BigDecimal valor = dec(m.path("operationValue"));
        LocalDate dia = LocalDate.parse(data);

        String id = unico(ids, "b3:movimentacao:" + data + ":" + (ativo == null ? "-" : ativo) + ":" + slug(tipo)
            + ":" + (lado == MovementSide.SELL ? "D" : "C") + ":" + plain(quantidade) + ":" + plain(valor));
        return new ImportedMovement(id, kind, ativo, lado, quantidade, "BRL", valor, null, null,
            inicioDoDia(dia), m.toString(), tipo, text(m, "participantName"), dia);
    }

    /** O tipo da Movimentacao da B3 no MovementKind (6.4, 6.6). Sem regra = OTHER, com o tipo no motivo. */
    static MovementKind classificar(String tipo) {
        if (tipo == null) {
            return MovementKind.OTHER;
        }
        String t = normalize(tipo);
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

    List<ImportedMovement> proventosAReceber(InvestorDocument doc, LocalDate fim, Set<String> ids) {
        String rota = props.getPaths().getProvisionedEvents();
        if (blank(rota)) {
            return List.of();
        }
        List<ImportedMovement> out = new ArrayList<>();
        for (JsonNode pagina : client.getAll(rota, vars(doc, null), periodo(fim, fim))) {
            for (JsonNode e : lista(pagina.path("data"), "Events")) {
                String ticker = text(e, "tickerSymbol");
                String tipo = text(e, "corporateActionTypeDescription");
                String pagamento = first(e, "PaymentDate", "paymentDate");
                LocalDate dia = pagamento == null ? null : LocalDate.parse(pagamento);
                BigDecimal quantidade = dec(e.path("eventQuantity"));
                BigDecimal valor = dec(firstNode(e, "netValue", "grossValue"));
                String id = unico(ids, "b3:provisionado:" + (pagamento == null ? "-" : pagamento) + ":"
                    + (ticker == null ? "-" : ticker) + ":" + slug(tipo) + ":" + plain(quantidade) + ":" + plain(valor));
                out.add(new ImportedMovement(id, MovementKind.CASH_EVENT, ticker, MovementSide.BUY, quantidade, "BRL",
                    valor, null, null, inicioDoDia(dia != null ? dia : fim), e.toString(),
                    (tipo == null ? "Provento" : tipo) + " a receber", text(e, "participantName"), dia));
            }
        }
        return out;
    }

    // --- posicao (divergencia, 6.7)

    Map<String, BigDecimal> posicao(InvestorDocument doc, LocalDate fim) {
        String rota = props.getPaths().getPosition();
        Map<String, BigDecimal> saldos = new TreeMap<>();
        if (blank(rota)) {
            return saldos;
        }
        for (JsonNode pagina : client.getAll(rota, vars(doc, "equities"), periodo(fim, fim))) {
            for (JsonNode p : lista(pagina.path("data"), "Positions")) {
                String ticker = text(p, "tickerSymbol");
                BigDecimal q = dec(firstNode(p, "equitiesQuantity", "quantity"));
                if (ticker != null && q != null && q.signum() != 0) {
                    saldos.merge(baseTicker(ticker), q, BigDecimal::add);
                }
            }
        }
        return saldos;
    }

    // ------------------------------------------------------------------ apoio

    private static Map<String, String> vars(InvestorDocument doc, String produto) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("documentNumber", doc.documentNumber());
        if (produto != null) {
            v.put("product", produto);
        }
        return v;
    }

    private static Map<String, Object> periodo(LocalDate inicio, LocalDate fim) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("referenceStartDate", inicio.toString());
        q.put("referenceEndDate", fim.toString());
        return q;
    }

    /**
     * A lista do corpo, tolerante ao aninhamento (A3): a primeira matriz cujo nome termina com o
     * sufixo, em {@code data} ou um nivel abaixo ({@code data.equitiesPeriods.equitiesMovements}).
     */
    static List<JsonNode> lista(JsonNode data, String sufixo) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode achada = procurar(data, sufixo, 0);
        if (achada != null) {
            achada.forEach(out::add);
        }
        return out;
    }

    private static JsonNode procurar(JsonNode no, String sufixo, int nivel) {
        if (no == null || !no.isObject() || nivel > 2) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> campos = no.fields();
        List<JsonNode> filhos = new ArrayList<>();
        while (campos.hasNext()) {
            Map.Entry<String, JsonNode> c = campos.next();
            if (c.getValue().isArray() && c.getKey().toLowerCase().endsWith(sufixo.toLowerCase())) {
                return c.getValue();
            }
            if (c.getValue().isObject()) {
                filhos.add(c.getValue());
            }
        }
        for (JsonNode f : filhos) {
            JsonNode r = procurar(f, sufixo, nivel + 1);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /** Compra/Venda, Credito/Debito, Entrada/Saida (com ou sem acento). */
    static MovementSide lado(String s) {
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

    private static String letra(MovementSide lado) {
        return lado == null ? "?" : lado == MovementSide.BUY ? "C" : "V";
    }

    private static Instant inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(SAO_PAULO).toInstant();
    }

    /** Id unico na busca (dois lancamentos iguais no mesmo dia ganham #2, #3) e dentro do limite da coluna. */
    private static String unico(Set<String> ids, String base) {
        String id = base.length() > MAX_ID ? base.substring(0, MAX_ID - 9) + ":" + Integer.toHexString(base.hashCode()) : base;
        String candidato = id;
        for (int n = 2; !ids.add(candidato); n++) {
            candidato = id + "#" + n;
        }
        return candidato;
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

    private static String text(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText().strip();
    }

    private static String first(JsonNode n, String... campos) {
        for (String c : campos) {
            String v = text(n, c);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode n, String... campos) {
        for (String c : campos) {
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
