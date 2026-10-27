package com.activest.adm.integration.ptax;

import com.activest.adm.integration.indices.IndexSourceException;
import com.activest.adm.integration.indices.IndexYearCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * API PTAX do Banco Central (Olinda), {@code CotacaoMoedaPeriodo} — o câmbio do cadastro de moedas
 * (docs/planos/PLANO_CADASTRO_MOEDAS.md, D7 e 9.3). Sem token.
 *
 * <p>Uma consulta por ano civil e moeda. Resposta: {@code {"value": [{"cotacaoCompra": 5.0, "cotacaoVenda": 5.0006,
 * "dataHoraCotacao": "2024-01-02 13:04:31.402", "tipoBoletim": "Fechamento"}, ...]}}, com os boletins de abertura,
 * intermediários e de fechamento de cada dia. Fica <b>só o de fechamento</b> (D22). Cache por ano em
 * {@code carga/data/bcb/ptax/{moeda}/{ano}.json} ({@link IndexYearCache}: ano fechado com arquivo não vai à fonte; o
 * corrente e o anterior sempre vão).</p>
 */
@Component
public class PtaxClient {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter PARAM = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IndexYearCache cache;
    private final String baseUrl;
    private final Path dir;
    private final Clock clock;

    /** O boletim de fechamento de um dia. */
    public record Bulletin(LocalDate date, BigDecimal buy, BigDecimal sell, LocalDateTime at) {
    }

    @Autowired
    public PtaxClient(@Value("${bcb.ptax-base-url:https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata}") String baseUrl,
                      @Value("${activest.data.dir:../carga/data}") String dataDir,
                      @Value("${indices.timeout-seconds:20}") long timeoutSeconds,
                      @Value("${indices.attempts:4}") int attempts,
                      @Value("${indices.retry-base-millis:1000}") long retryBaseMillis) {
        this(baseUrl, Path.of(dataDir, "bcb", "ptax"),
                new IndexYearCache(Duration.ofSeconds(timeoutSeconds), attempts, retryBaseMillis), Clock.system(ZONE));
    }

    PtaxClient(String baseUrl, Path dir, IndexYearCache cache, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dir = dir.toAbsolutePath().normalize();
        this.cache = cache;
        this.clock = clock;
    }

    /** Os boletins de fechamento da moeda no ano, em ordem de data. Ano corrente e anterior sempre vão à fonte. */
    public List<Bulletin> year(String currency, int year) {
        int current = LocalDate.now(clock).getYear();
        LocalDate last = LocalDate.of(year, 12, 31);
        String query = "@moeda='" + currency + "'&@dataInicial='" + LocalDate.of(year, 1, 1).format(PARAM)
                + "'&@dataFinalCotacao='" + last.format(PARAM) + "'&$format=json"
                + "&$select=cotacaoCompra,cotacaoVenda,dataHoraCotacao,tipoBoletim";
        URI url = URI.create(baseUrl + "/CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,"
                + "dataFinalCotacao=@dataFinalCotacao)?" + encode(query));
        String body = cache.fetch(url, dir.resolve(currency).resolve(year + ".json"), year >= current - 1,
                "{\"value\":[]}", PtaxClient::isValid);
        return parse(body);
    }

    /** Codifica só o que a URI não aceita cru (aspas, espaço), mantendo {@code @ = & $ ,}. */
    private static String encode(String query) {
        StringBuilder sb = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (c == '\'' || c == ' ') {
                sb.append(URLEncoder.encode(String.valueOf(c), StandardCharsets.UTF_8));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static boolean isValid(String body) {
        try {
            return JSON.readTree(body).path("value").isArray();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Fica o boletim "Fechamento" de cada dia. Dia sem ele (só abertura/intermediários — o dia corrente antes das 13h)
     * não entra: o câmbio do dia é o de fechamento.
     */
    static List<Bulletin> parse(String body) {
        try {
            JsonNode root = JSON.readTree(body).path("value");
            Map<LocalDate, Bulletin> byDate = new TreeMap<>();
            for (JsonNode n : root) {
                String type = n.path("tipoBoletim").asText("");
                if (!type.toLowerCase().startsWith("fechamento")) {
                    continue;
                }
                String when = n.path("dataHoraCotacao").asText("");
                if (when.length() < 10 || !n.hasNonNull("cotacaoCompra") || !n.hasNonNull("cotacaoVenda")) {
                    continue;
                }
                LocalDateTime at = parseTime(when);
                byDate.put(at.toLocalDate(), new Bulletin(at.toLocalDate(), n.get("cotacaoCompra").decimalValue(),
                        n.get("cotacaoVenda").decimalValue(), at));
            }
            return new ArrayList<>(byDate.values());
        } catch (IOException | RuntimeException e) {
            throw new IndexSourceException("Resposta da PTAX ilegivel: " + e.getMessage(), e);
        }
    }

    private static LocalDateTime parseTime(String s) {
        String t = s.replace('T', ' ');
        LocalDate d = LocalDate.parse(t.substring(0, 10));
        if (t.length() >= 19) {
            return d.atTime(Integer.parseInt(t.substring(11, 13)), Integer.parseInt(t.substring(14, 16)),
                    Integer.parseInt(t.substring(17, 19)));
        }
        return d.atStartOfDay();
    }
}
