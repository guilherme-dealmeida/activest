package com.primevest.adm.integration.brapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O cliente da BRAPI contra respostas gravadas — sem rede e sem dependencia
 * nova: o WebClient recebe uma {@link ExchangeFunction} falsa (PLANO_BRAPI.md, F9).
 *
 * <p>Os corpos sao os que a BRAPI devolveu em 24/09/2026.</p>
 */
class BrApiClientTest {

    private static final String QUOTE_PETR4 = """
        {"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","changed":false,"data":{"shortName":"PETR4",
        "longName":"Petroleo Brasileiro SA Pfd","currency":"BRL","regularMarketPrice":49.6,"regularMarketDayHigh":50.06,
        "regularMarketDayLow":48.52,"regularMarketChange":1.25,"regularMarketChangePercent":2.59,
        "regularMarketTime":"2026-09-23T21:31:30.000Z","marketCap":652726596035,"regularMarketVolume":43570600,
        "regularMarketPreviousClose":49.72,"regularMarketOpen":48.55,"logourl":"https://icons.brapi.dev/icons/PETR4.svg"}}],
        "requestedAt":"2026-09-24T04:22:13.153Z","took":0}""";

    private static final String HIST_PETR4 = """
        {"results":[{"requestedSymbol":"PETR4","symbol":"PETR4","changed":false,"data":{"usedInterval":"1d","usedRange":"5d",
        "historicalDataPrice":[{"date":1790132400,"open":48.55,"high":50.06,"low":48.52,"close":49.6,"volume":43570600,"adjustedClose":49.6},
        {"date":1790046000,"open":47.56,"high":48.54,"low":47.3,"close":48.35,"volume":48852400,"adjustedClose":48.35}]}}]}""";

    private static final String NOT_FOUND = """
        {"error":true,"message":"Nenhum resultado encontrado para os símbolos informados","code":"NOT_FOUND"}""";

    /** Respostas enfileiradas e pedidos recebidos. */
    private static final class Fake implements ExchangeFunction {
        final Deque<ClientResponse> responses = new ConcurrentLinkedDeque<>();
        final List<ClientRequest> requests = new ArrayList<>();

        Fake reply(HttpStatus status, String body, String... headers) {
            ClientResponse.Builder b = ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("RateLimit-Remaining", "14876")
                    .body(body);
            for (int i = 0; i + 1 < headers.length; i += 2) {
                b.header(headers[i], headers[i + 1]);
            }
            responses.add(b.build());
            return this;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(request);
            return Mono.just(responses.removeFirst());
        }
    }

    private final List<Long> sleeps = new ArrayList<>();

    private BrApiClient client(Fake fake, String token) {
        BrApiClient client = new BrApiClient(WebClient.builder().exchangeFunction(fake), "https://brapi.dev/api", token, 5);
        client.sleeper = sleeps::add;
        return client;
    }

    @Test
    @DisplayName("200: devolve o results[0].data tipado e manda o token no cabecalho, nunca na URL")
    void cotacao200() {
        Fake fake = new Fake().reply(HttpStatus.OK, QUOTE_PETR4);

        BrApiQuote quote = client(fake, "tok-secreto").fetchQuote("PETR4");

        assertThat(quote.regularMarketPrice()).isEqualByComparingTo("49.6");
        assertThat(quote.regularMarketVolume()).isEqualTo(43570600L);
        ClientRequest req = fake.requests.get(0);
        assertThat(req.url().getPath()).isEqualTo("/api/v2/stocks/quote");
        assertThat(req.url().getQuery()).isEqualTo("symbols=PETR4");
        assertThat(req.url().toString()).doesNotContain("tok-secreto");
        assertThat(req.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer tok-secreto");
    }

    @Test
    @DisplayName("Guarda o RateLimit-Remaining da ultima resposta para o log da rodada")
    void guardaRateLimit() {
        BrApiClient client = client(new Fake().reply(HttpStatus.OK, QUOTE_PETR4), "t");
        client.fetchQuote("PETR4");

        assertThat(client.lastRateLimitRemaining()).isEqualTo("14876");
    }

    @Test
    @DisplayName("Historico: candles com o dia do pregao no fuso de Sao Paulo")
    void historico() {
        Fake fake = new Fake().reply(HttpStatus.OK, HIST_PETR4);

        BrApiHistory h = client(fake, "t").fetchDailyHistory("PETR4", "5d");

        assertThat(h.changed()).isFalse();
        assertThat(h.candles()).hasSize(2);
        assertThat(h.candles().get(0).day()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(h.candles().get(0).close()).isEqualByComparingTo("49.6");
        assertThat(fake.requests.get(0).url().getQuery()).isEqualTo("symbols=PETR4&range=5d&interval=1d");
    }

    @Test
    @DisplayName("400 fora do plano: excecao com o code da BRAPI, sem nova tentativa")
    void erro400() {
        Fake fake = new Fake().reply(HttpStatus.BAD_REQUEST,
                "{\"error\":true,\"message\":\"range invalido\",\"code\":\"INVALID_RANGE\"}");

        assertThatThrownBy(() -> client(fake, "t").fetchDailyHistory("PETR4", "1y"))
                .isInstanceOfSatisfying(BrApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.code()).isEqualTo("INVALID_RANGE");
                });
        assertThat(fake.requests).hasSize(1);
    }

    @Test
    @DisplayName("401 e 403: excecao marcada como token/plano — a rodada para")
    void erro401e403() {
        Fake fake = new Fake()
                .reply(HttpStatus.UNAUTHORIZED, "{\"error\":true,\"message\":\"token\",\"code\":\"UNAUTHORIZED\"}")
                .reply(HttpStatus.FORBIDDEN, "{\"error\":true,\"message\":\"plano\",\"code\":\"FEATURE_NOT_AVAILABLE\"}");
        BrApiClient client = client(fake, "t");

        assertThatThrownBy(() -> client.fetchQuote("PETR4"))
                .isInstanceOfSatisfying(BrApiException.class, e -> assertThat(e.isAuthOrPlan()).isTrue());
        assertThatThrownBy(() -> client.fetchQuote("PETR4"))
                .isInstanceOfSatisfying(BrApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(403);
                    assertThat(e.isAuthOrPlan()).isTrue();
                });
    }

    @Test
    @DisplayName("404: excecao NOT_FOUND — quem chama pula o ativo")
    void erro404() {
        Fake fake = new Fake().reply(HttpStatus.NOT_FOUND, NOT_FOUND);

        assertThatThrownBy(() -> client(fake, "t").fetchQuote("AHEB3F"))
                .isInstanceOfSatisfying(BrApiException.class, e -> {
                    assertThat(e.isNotFound()).isTrue();
                    assertThat(e.code()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    @DisplayName("429: espera o Retry-After e tenta UMA vez de novo")
    void erro429TentaDeNovo() {
        Fake fake = new Fake()
                .reply(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":true,\"code\":\"RATE_LIMIT\"}", HttpHeaders.RETRY_AFTER, "7")
                .reply(HttpStatus.OK, QUOTE_PETR4);

        BrApiQuote quote = client(fake, "t").fetchQuote("PETR4");

        assertThat(quote.regularMarketPrice()).isEqualByComparingTo("49.6");
        assertThat(sleeps).containsExactly(7000L);
        assertThat(fake.requests).hasSize(2);
    }

    @Test
    @DisplayName("429 duas vezes: a excecao sobe")
    void erro429Duplo() {
        Fake fake = new Fake()
                .reply(HttpStatus.TOO_MANY_REQUESTS, "{}", HttpHeaders.RETRY_AFTER, "1")
                .reply(HttpStatus.TOO_MANY_REQUESTS, "{}", HttpHeaders.RETRY_AFTER, "1");

        assertThatThrownBy(() -> client(fake, "t").fetchQuote("PETR4"))
                .isInstanceOfSatisfying(BrApiException.class, e -> assertThat(e.isRateLimited()).isTrue());
        assertThat(fake.requests).hasSize(2);
    }

    @Test
    @DisplayName("Sem token, nenhum cabecalho Authorization")
    void semToken() {
        Fake fake = new Fake().reply(HttpStatus.OK, QUOTE_PETR4);
        BrApiClient client = client(fake, "");

        client.fetchQuote("PETR4");

        assertThat(client.hasToken()).isFalse();
        assertThat(fake.requests.get(0).headers().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
