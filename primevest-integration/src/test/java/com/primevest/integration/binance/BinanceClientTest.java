package com.primevest.integration.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.primevest.domain.integration.credential.ApiKeyCredentials;
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

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O cliente da Binance contra respostas falsas — sem rede. Chave e segredo ficticios. */
class BinanceClientTest {

    private static final ApiKeyCredentials KEY = new ApiKeyCredentials("chave-ficticia-AB12", "segredo-ficticio-XYZ");
    private static final long SERVER = 1_700_000_000_000L;

    /** Respostas enfileiradas e pedidos recebidos. */
    private static final class Fake implements ExchangeFunction {
        final Deque<ClientResponse> responses = new ArrayDeque<>();
        final List<ClientRequest> requests = new ArrayList<>();

        Fake responds(HttpStatus status, String requestBody) {
            responses.add(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(requestBody).build());
            return this;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(request);
            return Mono.just(responses.removeFirst());
        }
    }

    private BinanceClient apiClient(Fake fake) {
        BinanceClient c = new BinanceClient(WebClient.builder().exchangeFunction(fake), "https://api.binance.com", 5);
        c.clock = () -> SERVER - 5_000; // relogio local 5 s atrasado
        c.sleeper = ms -> { };
        return c;
    }

    @Test
    @DisplayName("HMAC-SHA256 bate com o exemplo da documentacao da Binance")
    void documentationSignature() {
        assertThat(BinanceClient.sign(
            "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559",
            "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"))
            .isEqualTo("c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71");
    }

    @Test
    @DisplayName("GET assinado: sincroniza o relogio, assina a query exata, chave no cabecalho e segredo fora da URL")
    void getSigned() {
        Fake fake = new Fake()
            .responds(HttpStatus.OK, "{\"serverTime\":" + SERVER + "}")
            .responds(HttpStatus.OK, "[{\"qty\":\"0.00197802\"}]");

        JsonNode r = apiClient(fake).signedGet(KEY, "/api/v3/myTrades", Map.of("symbol", "BTCBRL"));

        assertThat(fake.requests).hasSize(2);
        ClientRequest requestBody = fake.requests.get(1);
        String query = requestBody.url().getRawQuery();
        assertThat(query).startsWith("symbol=BTCBRL&recvWindow=10000&timestamp=");
        // timestamp no relogio da Binance, nao no local.
        long ts = Long.parseLong(query.replaceAll(".*timestamp=(\\d+).*", "$1"));
        assertThat(ts).isBetween(SERVER - 100, SERVER + 100);
        String withoutSignature = query.substring(0, query.indexOf("&signature="));
        assertThat(query).endsWith("&signature=" + BinanceClient.sign(withoutSignature, KEY.apiSecret()));
        assertThat(requestBody.headers().getFirst("X-MBX-APIKEY")).isEqualTo(KEY.apiKey());
        assertThat(requestBody.url().toString()).doesNotContain(KEY.apiSecret()).doesNotContain(KEY.apiKey());
        // Decimal lido sem passar por double.
        assertThat(new BigDecimal(r.get(0).path("qty").asText())).isEqualByComparingTo("0.00197802");
    }

    @Test
    @DisplayName("-1021 (relogio fora da janela): sincroniza de novo e tenta UMA vez")
    void clockOutsideWindow() {
        Fake fake = new Fake()
            .responds(HttpStatus.OK, "{\"serverTime\":" + SERVER + "}")
            .responds(HttpStatus.BAD_REQUEST, "{\"code\":-1021,\"msg\":\"Timestamp outside of the recvWindow.\"}")
            .responds(HttpStatus.OK, "{\"serverTime\":" + SERVER + "}")
            .responds(HttpStatus.OK, "{\"balances\":[]}");

        JsonNode r = apiClient(fake).signedGet(KEY, "/api/v3/account", Map.of());

        assertThat(r.has("balances")).isTrue();
        assertThat(fake.requests).extracting(p -> p.url().getPath())
            .containsExactly("/api/v3/time", "/api/v3/account", "/api/v3/time", "/api/v3/account");
    }

    @Test
    @DisplayName("Chave recusada: excecao com o codigo da Binance e sem chave, segredo ou assinatura na mensagem")
    void rejectedKey() {
        Fake fake = new Fake()
            .responds(HttpStatus.OK, "{\"serverTime\":" + SERVER + "}")
            .responds(HttpStatus.UNAUTHORIZED, "{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions for action.\"}");

        assertThatThrownBy(() -> apiClient(fake).signedGet(KEY, "/sapi/v1/account/apiRestrictions", Map.of()))
            .isInstanceOfSatisfying(BinanceException.class, e -> {
                assertThat(e.isCredentialRejected()).isTrue();
                assertThat(e.code()).isEqualTo(-2015);
                assertThat(e.getMessage()).contains("-2015").doesNotContain(KEY.apiKey())
                    .doesNotContain(KEY.apiSecret()).doesNotContain("signature");
            });
    }

    @Test
    @DisplayName("Peso do minuto acima do limiar: a proxima chamada espera o minuto virar")
    void weightBudget() {
        Fake fake = new Fake();
        fake.responses.add(ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header("X-MBX-USED-WEIGHT-1M", "5000").body("{\"symbols\":[]}").build());
        fake.responds(HttpStatus.OK, "{\"symbols\":[]}");
        List<Long> waits = new ArrayList<>();
        BinanceClient c = new BinanceClient(WebClient.builder().exchangeFunction(fake), "https://api.binance.com", 5, 4800);
        c.clock = () -> SERVER; // 1_700_000_000_000 = 22:13:20 UTC: faltam 40 s para o minuto virar
        c.sleeper = waits::add;

        c.publicGet("/api/v3/exchangeInfo", Map.of());
        assertThat(c.usedWeight()).isEqualTo(5000);
        c.publicGet("/api/v3/exchangeInfo", Map.of());

        assertThat(waits).singleElement().satisfies(ms -> assertThat(ms).isBetween(40_000L, 42_000L));
    }

    @Test
    @DisplayName("429: espera o Retry-After e tenta UMA vez")
    void limitExceeded() {
        Fake fake = new Fake();
        fake.responses.add(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.RETRY_AFTER, "7").body("{\"code\":-1003,\"msg\":\"Too many requests\"}").build());
        fake.responds(HttpStatus.OK, "{\"symbols\":[]}");
        List<Long> waits = new ArrayList<>();
        BinanceClient c = apiClient(fake);
        c.sleeper = waits::add;

        JsonNode r = c.publicGet("/api/v3/exchangeInfo", Map.of());

        assertThat(r.has("symbols")).isTrue();
        assertThat(waits).containsExactly(7_000L);
    }

    @Test
    @DisplayName("GET publico nao assina nem manda chave")
    void getPublic() {
        Fake fake = new Fake().responds(HttpStatus.OK, "{\"symbols\":[]}");

        apiClient(fake).publicGet("/api/v3/exchangeInfo", Map.of());

        ClientRequest requestBody = fake.requests.get(0);
        assertThat(requestBody.url().getRawQuery()).isNull();
        assertThat(requestBody.headers().containsKey("X-MBX-APIKEY")).isFalse();
    }
}
