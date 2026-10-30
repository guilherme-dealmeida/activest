package com.activest.adm.integration.foreign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenFIGI ({@code POST /v3/mapping}): ticker dos EUA → FIGI e o {@code securityType} que decide o tipo (D5, C2 —
 * docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md). A chave ({@code OPENFIGI_API_KEY}) é opcional: com ela, 100 tickers por
 * pedido e 25 pedidos a cada 6 s; sem ela, 10 por pedido e 25 por minuto.
 *
 * <p>Cache por ticker em {@code carga/data/openfigi/ticker/{TICKER}.json} (a resposta do ticker, achada ou não): o
 * ticker em disco não volta à rede, salvo com {@code refresh}. Recusa por ritmo (HTTP 429): espera e tenta de novo.</p>
 */
@Slf4j
@Component
public class OpenFigiClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI url;
    private final String apiKey;
    private final Path dir;
    private final int batch;
    private final long paceMillis;
    private long lastRequest;

    @Autowired
    public OpenFigiClient(@Value("${foreign.openfigi.url:https://api.openfigi.com/v3/mapping}") String url,
                          @Value("${foreign.openfigi.api-key:${OPENFIGI_API_KEY:}}") String apiKey,
                          @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this(URI.create(url), apiKey, Path.of(dataDir, "openfigi"));
    }

    OpenFigiClient(URI url, String apiKey, Path dir) {
        this.url = url;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        this.dir = dir.toAbsolutePath().normalize();
        this.batch = this.apiKey != null ? 100 : 10;
        // 25 pedidos por 6 s (com chave) ou por 60 s (sem): um pedido a cada 250 ms ou 2,5 s
        this.paceMillis = this.apiKey != null ? 250 : 2_500;
    }

    /** Um papel achado. {@code securityType}: Common Stock, REIT, ETP, ADR, Preference, Closed-End Fund... */
    public record Figi(String figi, String compositeFigi, String name, String ticker, String exchCode,
                       String securityType, String securityType2, String marketSector, String shareClassFigi) {
    }

    /**
     * O FIGI de cada ticker (forma Bloomberg: {@code BRK/B}), na bolsa composta dos EUA ({@code exchCode = US}).
     * Ticker sem resposta não entra no mapa.
     *
     * @param refresh pergunta de novo também o que está em disco
     */
    public Map<String, Figi> mapTickers(List<String> tickers, boolean refresh) {
        return mapTickers(tickers, refresh ? Duration.ZERO : null);
    }

    /**
     * Como {@link #mapTickers(List, boolean)}, perguntando de novo o ticker cujo arquivo em disco tem mais de
     * {@code maxAge} (nulo = o do disco vale sempre): o ticker reaproveitado por outro papel muda de FIGI.
     */
    public Map<String, Figi> mapTickers(List<String> tickers, Duration maxAge) {
        Map<String, Figi> out = new LinkedHashMap<>();
        List<String> ask = new ArrayList<>();
        for (String t : tickers) {
            Path f = cacheFile(t);
            if (Files.isRegularFile(f) && fresh(f, maxAge)) {
                read(f).flatMap(OpenFigiClient::pick).ifPresent(h -> out.put(t, h));
            } else {
                ask.add(t);
            }
        }
        for (int i = 0; i < ask.size(); i += batch) {
            List<String> part = ask.subList(i, Math.min(ask.size(), i + batch));
            JsonNode answer = post(part);
            for (int j = 0; j < part.size(); j++) {
                JsonNode one = answer.get(j);
                String t = part.get(j);
                if (one == null || one.has("error")) {
                    log.warn("OpenFIGI: {} sem resposta ({})", t, one == null ? "?" : one.path("error").asText());
                    continue;
                }
                write(cacheFile(t), one);
                pick(one).ifPresent(h -> out.put(t, h));
            }
        }
        return out;
    }

    /** O papel da resposta: o da bolsa composta (US), com FIGI composto. */
    static Optional<Figi> pick(JsonNode one) {
        JsonNode data = one.path("data");
        JsonNode best = null;
        for (JsonNode d : data) {
            if ("US".equals(d.path("exchCode").asText()) && d.path("figi").asText().equals(d.path("compositeFIGI").asText())) {
                best = d;
                break;
            }
            if (best == null) {
                best = d;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(new Figi(text(best, "figi"), text(best, "compositeFIGI"), text(best, "name"),
                text(best, "ticker"), text(best, "exchCode"), text(best, "securityType"), text(best, "securityType2"),
                text(best, "marketSector"), text(best, "shareClassFIGI")));
    }

    private JsonNode post(List<String> tickers) {
        ArrayNode jobs = mapper.createArrayNode();
        for (String t : tickers) {
            ObjectNode job = jobs.addObject();
            job.put("idType", "TICKER");
            job.put("idValue", t);
            job.put("exchCode", "US");
        }
        for (int attempt = 1; ; attempt++) {
            pace();
            try {
                HttpRequest.Builder req = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(jobs)));
                if (apiKey != null) {
                    req.header("X-OPENFIGI-APIKEY", apiKey);
                }
                HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 429 && attempt < 6) {
                    long wait = resp.headers().firstValue("ratelimit-reset").map(Long::parseLong).orElse(10L);
                    log.info("OpenFIGI: ritmo excedido, espera {} s (tentativa {})", wait, attempt);
                    sleep(Math.max(1, wait) * 1000);
                    continue;
                }
                if (resp.statusCode() != 200) {
                    throw new IllegalStateException("OpenFIGI respondeu HTTP " + resp.statusCode());
                }
                JsonNode body = mapper.readTree(resp.body());
                if (!body.isArray() || body.size() != tickers.size()) {
                    throw new IllegalStateException("OpenFIGI: resposta fora do formato (" + tickers.size() + " pedidos)");
                }
                return body;
            } catch (IOException e) {
                if (attempt >= 3) {
                    throw new UncheckedIOException("OpenFIGI indisponível", e);
                }
                sleep(2_000L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OpenFIGI interrompido", e);
            }
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

    private static boolean fresh(Path f, Duration maxAge) {
        if (maxAge == null) {
            return true;
        }
        try {
            return Files.getLastModifiedTime(f).toInstant().isAfter(java.time.Instant.now().minus(maxAge));
        } catch (IOException e) {
            return false;
        }
    }

    /** Há chave: o lote grande e o ritmo rápido (o catálogo inteiro em segundos). */
    public boolean hasKey() {
        return apiKey != null;
    }

    Path cacheFile(String ticker) {
        return dir.resolve("ticker").resolve(ticker.toUpperCase().replaceAll("[^A-Z0-9._-]", "_") + ".json");
    }

    private Optional<JsonNode> read(Path f) {
        try {
            return Optional.of(mapper.readTree(f.toFile()));
        } catch (IOException e) {
            log.warn("OpenFIGI: cache ilegível {}: {}", f, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(Path f, JsonNode one) {
        try {
            Files.createDirectories(f.getParent());
            mapper.writeValue(f.toFile(), one);
        } catch (IOException e) {
            log.warn("OpenFIGI: não gravou o cache {}: {}", f, e.getMessage());
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
