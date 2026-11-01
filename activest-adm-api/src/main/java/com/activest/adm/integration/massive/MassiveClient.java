package com.activest.adm.integration.massive;

import com.activest.adm.integration.foreign.ForeignQuotesProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Massive (ex-Polygon) grouped daily: {@code GET /v2/aggs/grouped/locale/us/market/stocks/{data}?adjusted=false}
 * devolve o fechamento não ajustado de todos os papéis dos EUA num pregão (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md).
 *
 * <ul>
 *   <li>Chave em {@code MASSIVE_API_KEY}, enviada no cabeçalho {@code Authorization} (nunca na URL dos logs).</li>
 *   <li>Plano gratuito: 5 pedidos por minuto — um pedido a cada {@code pace} (12 s); 429 espera 60 s e repete.</li>
 *   <li>403 num dia que ainda é o corrente em Nova York = dia não liberado; 403 num dia passado = fora dos 2 anos do
 *   plano (erro de entrada); 401 = chave recusada (I5).</li>
 *   <li>O corpo do pregão com cotações fica em {@code carga/data/massive/grouped/AAAA/AAAA-MM-DD.json.gz} (I1): o dia
 *   em disco não volta à rede.</li>
 *   <li>Ticker da fonte → catálogo (D5): a preferencial vem com {@code p} minúsculo ({@code ABRpE} → {@code ABR-E}); a
 *   classe já vem com ponto ({@code BRK.B}).</li>
 *   <li>Volume com casas (frações de ação dos EUA) arredondado para o inteiro mais próximo (PENDENCIAS 44.1).</li>
 * </ul>
 */
@Slf4j
@Component
public class MassiveClient implements ForeignQuotesProvider {

    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final Path dir;
    private final Clock clock;
    private final long paceMillis;
    private final long rateLimitWaitMillis;
    private long lastRequest;

    @Autowired
    public MassiveClient(@Value("${foreign.massive.url:https://api.massive.com}") String baseUrl,
                         @Value("${foreign.massive.api-key:${MASSIVE_API_KEY:}}") String apiKey,
                         @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this(baseUrl, apiKey, Path.of(dataDir, "massive", "grouped"), Clock.systemDefaultZone(), 12_000, 60_000);
    }

    MassiveClient(String baseUrl, String apiKey, Path dir, Clock clock, long paceMillis, long rateLimitWaitMillis) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        this.dir = dir.toAbsolutePath().normalize();
        this.clock = clock;
        this.paceMillis = paceMillis;
        this.rateLimitWaitMillis = rateLimitWaitMillis;
    }

    @Override
    public String sourceName() {
        return "Massive";
    }

    @Override
    public DayQuotes day(LocalDate date) {
        Path file = file(date);
        if (Files.isRegularFile(file)) {
            try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
                List<Quote> quotes = parse(mapper.readTree(in));
                if (!quotes.isEmpty()) {
                    return new DayQuotes(date, Status.OK, quotes, true);
                }
            } catch (IOException e) {
                log.warn("Massive: arquivo {} ilegível ({}); baixando de novo", file, e.getMessage());
            }
        }
        if (apiKey == null) {
            throw new AuthException("MASSIVE_API_KEY não configurada");
        }
        HttpResponse<byte[]> resp = get(date);
        int code = resp.statusCode();
        if (code == 401) {
            throw new AuthException("Massive recusou a chave (HTTP 401)");
        }
        if (code == 403) {
            if (!date.isBefore(LocalDate.now(clock.withZone(NEW_YORK)))) {
                return DayQuotes.of(date, Status.NOT_RELEASED);
            }
            throw new OutOfRangeException("Massive não entrega " + date + " no plano atual (HTTP 403: "
                    + message(resp.body()) + ")");
        }
        if (code != 200) {
            throw new UnavailableException("Massive respondeu HTTP " + code + " para " + date + ": "
                    + message(resp.body()), null);
        }
        List<Quote> quotes;
        try {
            quotes = parse(mapper.readTree(resp.body()));
        } catch (IOException | RuntimeException e) {
            throw new UnavailableException("Massive: resposta de " + date + " fora do formato", e);
        }
        if (quotes.isEmpty()) {
            return DayQuotes.of(date, Status.NO_SESSION);
        }
        save(file, resp.body());
        return new DayQuotes(date, Status.OK, quotes, false);
    }

    /** O arquivo guardado do pregão. */
    public Path file(LocalDate date) {
        return dir.resolve(String.valueOf(date.getYear())).resolve(date + ".json.gz");
    }

    // ------------------------------------------------------------------ leitura

    static List<Quote> parse(JsonNode body) {
        JsonNode results = body.path("results");
        List<Quote> out = new ArrayList<>(Math.max(results.size(), 0));
        for (JsonNode r : results) {
            String ticker = r.path("T").asText("");
            BigDecimal close = decimal(r, "c");
            if (ticker.isEmpty() || close == null) {
                continue;
            }
            BigDecimal volume = decimal(r, "v");
            out.add(new Quote(catalogTicker(ticker), decimal(r, "o"), decimal(r, "h"), decimal(r, "l"), close,
                    volume == null ? 0L : volume.setScale(0, RoundingMode.HALF_UP).longValueExact()));
        }
        return out;
    }

    /** Grafia do Massive → catálogo (D5): o {@code p} minúsculo da preferencial vira hífen ({@code ABRpE} → {@code ABR-E}). */
    static String catalogTicker(String sourceTicker) {
        return sourceTicker.replace('p', '-');
    }

    private static BigDecimal decimal(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || !n.isNumber() ? null : n.decimalValue();
    }

    private String message(byte[] body) {
        try {
            JsonNode n = mapper.readTree(body);
            String m = n.path("message").asText(n.path("error").asText(""));
            return m.isEmpty() ? n.path("status").asText("?") : m;
        } catch (IOException | RuntimeException e) {
            return "?";
        }
    }

    // ------------------------------------------------------------------ rede e disco

    private HttpResponse<byte[]> get(LocalDate date) {
        URI uri = URI.create(baseUrl + "/v2/aggs/grouped/locale/us/market/stocks/" + date + "?adjusted=false");
        for (int attempt = 1; ; attempt++) {
            pace();
            try {
                HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120))
                        .header("Authorization", "Bearer " + apiKey).GET().build();
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if ((resp.statusCode() == 429 || resp.statusCode() >= 500) && attempt < MAX_ATTEMPTS) {
                    log.info("Massive: HTTP {} em {} — espera {} s (tentativa {})", resp.statusCode(), date,
                            rateLimitWaitMillis / 1000, attempt);
                    sleep(rateLimitWaitMillis);
                    continue;
                }
                return resp;
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new UnavailableException("Massive indisponível: " + e.getMessage(), e);
                }
                sleep(Math.min(rateLimitWaitMillis, 5_000L * attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnavailableException("Massive interrompido", e);
            }
        }
    }

    private void save(Path file, byte[] body) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
                out.write(body);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // o pregão já está na memória: sem a cópia, a próxima rodada só baixa de novo
            log.warn("Massive: não foi possível guardar {}: {}", file, e.getMessage());
        }
    }

    private synchronized void pace() {
        long wait = lastRequest + paceMillis - System.currentTimeMillis();
        if (wait > 0) {
            sleep(wait);
        }
        lastRequest = System.currentTimeMillis();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
