package com.primevest.integration.brapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Cliente da BRAPI (brapi.dev) — endpoints v2, plano gratuito.
 *
 * <p>O que o plano gratuito permite (PLANO_BRAPI.md, secao 1) e o que este
 * cliente usa:</p>
 * <ul>
 *   <li>{@code GET /v2/stocks/quote?symbols=X} — cotacao, 1 ativo por chamada;</li>
 *   <li>{@code GET /v2/stocks/historical?symbols=X&range=5d&interval=1d} —
 *   OHLCV diario, ate {@code 3mo};</li>
 *   <li>{@code GET /v2/tickers} e {@code /v2/tickers/renames} — lista oficial e
 *   renomes, sem custo de cota.</li>
 * </ul>
 *
 * <p><b>Token</b> no cabecalho {@code Authorization: Bearer}, lido de
 * {@code BRAPI_TOKEN} (o .env da raiz, fora do git). Nunca na URL, nunca no
 * log, nunca no front.</p>
 *
 * <p><b>Erros</b> sobem como {@link BrApiException} com o status e o
 * {@code code} da BRAPI — nada de {@code catch} devolvendo vazio. O 429 e
 * tentado de novo UMA vez, depois do {@code Retry-After}.</p>
 *
 * <p>O cabecalho {@code RateLimit-Remaining} da ultima resposta fica guardado
 * ({@link #lastRateLimitRemaining()}) para a rotina registrar no fim da rodada.</p>
 */
@Slf4j
@Component
public class BrApiClient {

    /** O plano gratuito aceita 1 ativo por chamada; lote volta 400. */
    public static final int MAX_SYMBOLS_PER_REQUEST = 1;

    private static final long MAX_RETRY_AFTER_SECONDS = 60;
    private static final int TICKERS_PAGE_SIZE = 500;

    private final WebClient webClient;
    private final String token;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> rateLimitRemaining = new AtomicReference<>();

    /** Espera do 429. Troca nos testes para nao dormir de verdade. */
    Sleeper sleeper = millis -> Thread.sleep(millis);

    @Autowired
    public BrApiClient(WebClient.Builder builder,
                       @Value("${brapi.base-url}") String baseUrl,
                       @Value("${brapi.token:}") String token,
                       @Value("${brapi.timeout-seconds:20}") long timeoutSeconds) {
        this.webClient = builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.token = token;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** Ha token configurado. Sem ele a BRAPI so responde 4 tickers de teste. */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    /**
     * Cotacao de um ativo.
     *
     * @return o {@code results[0].data} da resposta
     * @throws BrApiException resposta nao-2xx (404 = ativo nao encontrado) ou falha de rede
     */
    public BrApiQuote fetchQuote(String ticker) {
        QuoteResponse response = get(uri -> uri.path("/v2/stocks/quote")
                .queryParam("symbols", ticker)
                .build(), QuoteResponse.class);
        if (response == null || response.results() == null || response.results().isEmpty()
                || response.results().get(0).data() == null) {
            throw new BrApiException(404, "NOT_FOUND", "Resposta sem results[0].data para " + ticker, null);
        }
        return response.results().get(0).data();
    }

    /**
     * Historico diario (OHLCV) de um ativo.
     *
     * @param range {@code 5d}, {@code 1mo} ou {@code 3mo} — o maximo do plano gratuito
     */
    public BrApiHistory fetchDailyHistory(String ticker, String range) {
        HistoricalResponse response = get(uri -> uri.path("/v2/stocks/historical")
                .queryParam("symbols", ticker)
                .queryParam("range", range)
                .queryParam("interval", "1d")
                .build(), HistoricalResponse.class);
        if (response == null || response.results() == null || response.results().isEmpty()) {
            throw new BrApiException(404, "NOT_FOUND", "Resposta sem historico para " + ticker, null);
        }
        HistoricalResult result = response.results().get(0);
        List<BrApiCandle> candles = result.data() != null && result.data().historicalDataPrice() != null
                ? result.data().historicalDataPrice()
                : List.of();
        return new BrApiHistory(result.requestedSymbol(), result.symbol(),
                Boolean.TRUE.equals(result.changed()), candles);
    }

    /**
     * Lista oficial de tickers de um subtipo ({@code stock}, {@code unit},
     * {@code fii}, {@code etf}, {@code bdr}), todas as paginas. Nao gasta cota.
     */
    public List<BrApiTicker> fetchTickers(String subType) {
        List<BrApiTicker> all = new ArrayList<>();
        for (int page = 1; page <= 20; page++) {
            final int p = page;
            TickersResponse response = get(uri -> uri.path("/v2/tickers")
                    .queryParam("subType", subType)
                    .queryParam("limit", TICKERS_PAGE_SIZE)
                    .queryParam("page", p)
                    .queryParam("sortBy", "symbol")
                    .queryParam("sortOrder", "asc")
                    .build(), TickersResponse.class);
            List<BrApiTicker> results = response != null && response.results() != null ? response.results() : List.of();
            all.addAll(results);
            if (results.size() < TICKERS_PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    /** Renomes de ticker conhecidos pela BRAPI. Nao gasta cota. */
    public List<BrApiRename> fetchRenames() {
        RenamesResponse response = get(uri -> uri.path("/v2/tickers/renames").build(), RenamesResponse.class);
        return response != null && response.results() != null ? response.results() : List.of();
    }

    /** {@code RateLimit-Remaining} da ultima resposta. Nulo se ainda nao houve chamada. */
    public String lastRateLimitRemaining() {
        return rateLimitRemaining.get();
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private <T> T get(Function<UriBuilder, URI> uri, Class<T> type) {
        try {
            return once(uri, type);
        } catch (BrApiException e) {
            if (!e.isRateLimited()) {
                throw e;
            }
            long wait = Math.min(e.retryAfterSeconds() != null ? e.retryAfterSeconds() : 5, MAX_RETRY_AFTER_SECONDS);
            log.warn("BRAPI 429 ({}): nova tentativa em {} s", e.code(), wait);
            try {
                sleeper.sleep(wait * 1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return once(uri, type);
        }
    }

    private <T> T once(Function<UriBuilder, URI> uri, Class<T> type) {
        try {
            return webClient.get()
                    .uri(uri)
                    .headers(h -> {
                        if (hasToken()) {
                            h.setBearerAuth(token);
                        }
                    })
                    .exchangeToMono(resp -> handle(resp, type))
                    .timeout(timeout)
                    .block();
        } catch (BrApiException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new BrApiException(0, "NETWORK", "Falha de rede na BRAPI: " + cause, null);
        }
    }

    private <T> Mono<T> handle(ClientResponse resp, Class<T> type) {
        String remaining = resp.headers().asHttpHeaders().getFirst("RateLimit-Remaining");
        if (remaining != null) {
            rateLimitRemaining.set(remaining);
        }
        if (resp.statusCode().is2xxSuccessful()) {
            return resp.bodyToMono(type);
        }
        int status = resp.statusCode().value();
        Long retryAfter = parseLong(resp.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(toException(status, body, retryAfter)));
    }

    private BrApiException toException(int status, String body, Long retryAfter) {
        String code = null;
        String message = body;
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node != null && node.isObject()) {
                code = node.path("code").asText(null);
                message = node.path("message").asText(body);
            }
        } catch (Exception ignored) {
            // corpo nao e JSON: fica o texto
        }
        return new BrApiException(status, code, "BRAPI " + status + (code != null ? " " + code : "") + ": " + message,
                retryAfter);
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    // ------------------------------------------------------------------
    // Envelopes das respostas
    // ------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuoteResponse(List<QuoteResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuoteResult(String requestedSymbol, String symbol, Boolean changed, BrApiQuote data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoricalResponse(List<HistoricalResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoricalResult(String requestedSymbol, String symbol, Boolean changed, HistoricalData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoricalData(String usedInterval, String usedRange, List<BrApiCandle> historicalDataPrice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TickersResponse(List<BrApiTicker> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RenamesResponse(List<BrApiRename> results) {
    }
}
