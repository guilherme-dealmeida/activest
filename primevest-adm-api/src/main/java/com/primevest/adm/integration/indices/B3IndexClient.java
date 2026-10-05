package com.primevest.adm.integration.indices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.marketindex.IndexPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Fechamento diario dos indices da B3 (Ibovespa, IFIX) pelo site da B3 — o mesmo site, nao documentado, dos
 * proventos (docs/planos/PLANO_INDICES_MERCADO.md, 2.3).
 *
 * <p>{@code /indexStatisticsProxy/IndexCall/GetPortfolioDay/{base64 de {"index","language","year"}}} devolve o
 * ano numa grade: {@code results[]} com {@code day} (1 a 31) e {@code rateValue1..12} (o fechamento em cada
 * mes, texto pt-BR "132.696,63"; nulo sem pregao). Cache por ano em
 * {@code carga/data/b3/indices/{INDICE}/{ano}.json} ({@link IndexYearCache}).</p>
 */
@Component
public class B3IndexClient {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EMPTY = "{\"results\":[]}";

    private final IndexYearCache cache;
    private final String baseUrl;
    private final Path dir;
    private final Clock clock;

    @Autowired
    public B3IndexClient(@Value("${b3.base-url:https://sistemaswebb3-listados.b3.com.br}") String baseUrl,
                         @Value("${primevest.data.dir:../carga/data}") String dataDir,
                         @Value("${indices.timeout-seconds:20}") long timeoutSeconds,
                         @Value("${indices.attempts:4}") int attempts,
                         @Value("${indices.retry-base-millis:1000}") long retryBaseMillis) {
        this(baseUrl, Path.of(dataDir, "b3", "indices"),
                new IndexYearCache(Duration.ofSeconds(timeoutSeconds), attempts, retryBaseMillis), Clock.system(ZONE));
    }

    B3IndexClient(String baseUrl, Path dir, IndexYearCache cache, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dir = dir.toAbsolutePath().normalize();
        this.cache = cache;
        this.clock = clock;
    }

    /** Fechamentos do indice no ano, em ordem de data. Ano corrente e anterior sempre vao a fonte. */
    public List<IndexPoint> year(String index, int year) {
        int current = LocalDate.now(clock).getYear();
        String param = "{\"index\":\"" + index + "\",\"language\":\"pt-br\",\"year\":\"" + year + "\"}";
        String encoded = Base64.getEncoder().encodeToString(param.getBytes(StandardCharsets.UTF_8));
        URI url = URI.create(baseUrl + "/indexStatisticsProxy/IndexCall/GetPortfolioDay/" + encoded);
        String body = cache.fetch(url, dir.resolve(index).resolve(year + ".json"), year >= current - 1, EMPTY,
                B3IndexClient::hasResults);
        return parse(body, year);
    }

    static boolean hasResults(String body) {
        try {
            return JSON.readTree(body).path("results").isArray();
        } catch (IOException e) {
            return false;
        }
    }

    static List<IndexPoint> parse(String body, int year) {
        try {
            List<IndexPoint> points = new ArrayList<>();
            for (JsonNode row : JSON.readTree(body).path("results")) {
                int day = row.path("day").asInt(0);
                for (int month = 1; month <= 12; month++) {
                    JsonNode v = row.get("rateValue" + month);
                    if (day < 1 || v == null || v.isNull() || v.asText().isBlank()) {
                        continue;
                    }
                    LocalDate date;
                    try {
                        date = LocalDate.of(year, month, day);
                    } catch (DateTimeException e) {
                        continue;
                    }
                    points.add(new IndexPoint(date, ptBr(v.asText())));
                }
            }
            points.sort(Comparator.comparing(IndexPoint::date));
            return points;
        } catch (IOException | RuntimeException e) {
            throw new IndexSourceException("Resposta da B3 (indices) ilegivel: " + e.getMessage(), e);
        }
    }

    /** "132.696,63" → 132696.63. */
    static BigDecimal ptBr(String text) {
        return new BigDecimal(text.trim().replace(".", "").replace(',', '.'));
    }
}
