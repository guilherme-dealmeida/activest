package com.activest.adm.integration.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.adm.integration.indices.IndexYearCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Historico diario publico da Binance ({@code /api/v3/klines}, sem chave) — docs/planos/PLANO_RENTABILIDADE.md, 3.3
 * e D2. Fonte de <b>catalogo</b>: nada a ver com a integracao do usuario (activest-integration), que le a conta dele.
 *
 * <p>Um candle por dia UTC (abre 00:00 UTC, fecha 23:59:59 UTC = 20:59 em Sao Paulo); o fechamento vale para a data
 * UTC do candle. Cache por ano em {@code carga/data/binance/klines/{SIMBOLO}/{ano}.json}
 * ({@link IndexYearCache}): ano fechado com arquivo so do disco; o corrente e o anterior sempre da fonte.</p>
 */
@Component
public class BinanceKlinesClient {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EMPTY = "[]";

    /** Um dia: data UTC do candle e os precos (cotacao do par, ex.: BRL). */
    public record DailyCandle(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
    }

    private final IndexYearCache cache;
    private final String baseUrl;
    private final Path dir;
    private final Clock clock;

    @Autowired
    public BinanceKlinesClient(@Value("${binance.public-url:https://api.binance.com}") String baseUrl,
                               @Value("${activest.data.dir:../carga/data}") String dataDir,
                               @Value("${binance.timeout-seconds:20}") long timeoutSeconds,
                               @Value("${binance.attempts:4}") int attempts,
                               @Value("${binance.retry-base-millis:1000}") long retryBaseMillis) {
        this(baseUrl, Path.of(dataDir, "binance", "klines"),
                new IndexYearCache(Duration.ofSeconds(timeoutSeconds), attempts, retryBaseMillis), Clock.system(ZONE));
    }

    BinanceKlinesClient(String baseUrl, Path dir, IndexYearCache cache, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dir = dir.toAbsolutePath().normalize();
        this.cache = cache;
        this.clock = clock;
    }

    /** Candles diarios do par no ano, em ordem de data (no maximo 366: cabe numa chamada de 1000). */
    public List<DailyCandle> year(String symbol, int year) {
        int current = LocalDate.now(clock).getYear();
        long start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long end = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
        URI url = URI.create(baseUrl + "/api/v3/klines?symbol=" + symbol + "&interval=1d&startTime=" + start
                + "&endTime=" + end + "&limit=1000");
        String body = cache.fetch(url, dir.resolve(symbol).resolve(year + ".json"), year >= current - 1, EMPTY,
                BinanceKlinesClient::isArray);
        return parse(body);
    }

    static boolean isArray(String body) {
        try {
            return JSON.readTree(body).isArray();
        } catch (IOException e) {
            return false;
        }
    }

    /** {@code [[openTime, open, high, low, close, volume, closeTime, ...], ...]}. */
    static List<DailyCandle> parse(String body) {
        List<DailyCandle> out = new ArrayList<>();
        try {
            for (JsonNode k : JSON.readTree(body)) {
                if (!k.isArray() || k.size() < 5) {
                    continue;
                }
                LocalDate date = Instant.ofEpochMilli(k.get(0).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
                out.add(new DailyCandle(date, new BigDecimal(k.get(1).asText()), new BigDecimal(k.get(2).asText()),
                        new BigDecimal(k.get(3).asText()), new BigDecimal(k.get(4).asText())));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Resposta da Binance ilegivel: " + e.getMessage(), e);
        }
        return out;
    }
}
