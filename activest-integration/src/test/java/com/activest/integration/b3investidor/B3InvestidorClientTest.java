package com.activest.integration.b3investidor;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O cliente da B3 contra respostas falsas — sem rede. Credenciais e CPF ficticios. */
class B3InvestidorClientTest {

    private static final String CPF = "52998224725";
    private static final String TOKEN = "{\"token_type\":\"Bearer\",\"expires_in\":3599,\"access_token\":\"tok-%d\"}";
    private static final String ROUTE = "/api/position/v3/{product}/investors/{documentNumber}";
    private static final Map<String, String> VARS = Map.of("product", "equities", "documentNumber", CPF);
    private static final Map<String, Object> PERIOD = new LinkedHashMap<>();

    static {
        PERIOD.put("referenceStartDate", "2024-01-01");
        PERIOD.put("referenceEndDate", "2024-01-31");
    }

    /** Respostas enfileiradas e pedidos recebidos. */
    private static final class Fake implements ExchangeFunction {
        final Deque<ClientResponse> responses = new ArrayDeque<>();
        final List<ClientRequest> requests = new ArrayList<>();
        int tokens;

        Fake token() {
            tokens++;
            return responds(HttpStatus.OK, TOKEN.formatted(tokens));
        }

        Fake responds(HttpStatus status, String requestBody) {
            return responds(status, requestBody, null);
        }

        Fake responds(HttpStatus status, String requestBody, String retryAfter) {
            ClientResponse.Builder b = ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(requestBody);
            if (retryAfter != null) {
                b.header(HttpHeaders.RETRY_AFTER, retryAfter);
            }
            responses.add(b.build());
            return this;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(request);
            return Mono.just(responses.removeFirst());
        }

        List<ClientRequest> gets() {
            return requests.stream().filter(p -> p.method() == HttpMethod.GET).toList();
        }

        long posts() {
            return requests.stream().filter(p -> p.method() == HttpMethod.POST).count();
        }
    }

    private final List<Long> pauses = new ArrayList<>();
    private long now = 1_700_000_000_000L;

    private B3InvestidorClient apiClient(Fake fake) {
        B3InvestidorProperties props = new B3InvestidorProperties();
        props.setEnabled(true);
        props.setBaseUrl("https://b3.ficticia.test:2443/");
        props.setTokenUrl("https://login.ficticio.test/tenant/oauth2/v2.0/token");
        props.setClientId("cliente-ficticio");
        props.setClientSecret("segredo-ficticio");
        props.setScope("escopo-ficticio/.default");
        B3InvestidorClient c = new B3InvestidorClient(WebClient.builder().exchangeFunction(fake), props);
        c.clock = () -> now;
        c.sleeper = pauses::add;
        return c;
    }

    @BeforeEach
    void cleanUp() {
        pauses.clear();
    }

    @Test
    @DisplayName("Pede o token (client_credentials) uma vez e o usa como Bearer enquanto vale (A7)")
    void tokenEmCache() {
        Fake fake = new Fake().token()
            .responds(HttpStatus.OK, "{\"data\":{\"equitiesPositions\":[]}}")
            .responds(HttpStatus.OK, "{\"data\":{\"equitiesPositions\":[]}}");
        B3InvestidorClient c = apiClient(fake);

        c.get(ROUTE, VARS, PERIOD);
        c.get(ROUTE, VARS, PERIOD);

        assertThat(fake.posts()).isEqualTo(1);
        ClientRequest token = fake.requests.get(0);
        assertThat(token.url().toString()).isEqualTo("https://login.ficticio.test/tenant/oauth2/v2.0/token");
        ClientRequest get = fake.gets().get(0);
        assertThat(get.url().toString()).isEqualTo("https://b3.ficticia.test:2443/api/position/v3/equities/investors/"
            + CPF + "?referenceStartDate=2024-01-01&referenceEndDate=2024-01-31");
        assertThat(get.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer tok-1");
    }

    @Test
    @DisplayName("Renova o token 5 minutos antes de vencer")
    void renewsToken() {
        Fake fake = new Fake().token().responds(HttpStatus.OK, "{}").token().responds(HttpStatus.OK, "{}");
        B3InvestidorClient c = apiClient(fake);

        c.get(ROUTE, VARS, Map.of());
        now += (3599 - 299) * 1000L;
        c.get(ROUTE, VARS, Map.of());

        assertThat(fake.posts()).isEqualTo(2);
        assertThat(fake.gets().get(1).headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer tok-2");
    }

    @Test
    @DisplayName("401 da API: descarta o token, pede outro e tenta UMA vez")
    void reautentica() {
        Fake fake = new Fake().token()
            .responds(HttpStatus.UNAUTHORIZED, "{\"errors\":[{\"code\":\"401\",\"title\":\"Token expirado\"}]}")
            .token()
            .responds(HttpStatus.OK, "{\"ok\":true}");

        JsonNode r = apiClient(fake).get(ROUTE, VARS, Map.of());

        assertThat(r.path("ok").asBoolean()).isTrue();
        assertThat(fake.gets().get(1).headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer tok-2");
    }

    @Test
    @DisplayName("429: espera o Retry-After e tenta UMA vez (A8)")
    void limit() {
        Fake fake = new Fake().token()
            .responds(HttpStatus.TOO_MANY_REQUESTS, "{}", "3")
            .responds(HttpStatus.OK, "{\"ok\":true}");

        assertThat(apiClient(fake).get(ROUTE, VARS, Map.of()).path("ok").asBoolean()).isTrue();
        assertThat(pauses).containsExactly(3000L);
    }

    @Test
    @DisplayName("503 duas vezes: uma nova tentativa e o erro sobe com o status")
    void instability() {
        Fake fake = new Fake().token()
            .responds(HttpStatus.SERVICE_UNAVAILABLE, "{\"errors\":[{\"code\":\"503\",\"title\":\"Indisponível\"}]}")
            .responds(HttpStatus.SERVICE_UNAVAILABLE, "{\"errors\":[{\"code\":\"503\",\"title\":\"Indisponível\"}]}");

        assertThatThrownBy(() -> apiClient(fake).get(ROUTE, VARS, Map.of()))
            .isInstanceOf(B3InvestidorException.class)
            .satisfies(e -> assertThat(((B3InvestidorException) e).status()).isEqualTo(503));
        assertThat(fake.gets()).hasSize(2);
    }

    @Test
    @DisplayName("422 de negocio: sobe com o codigo da B3 e sem o CPF na mensagem")
    void businessError() {
        Fake fake = new Fake().token().responds(HttpStatus.UNPROCESSABLE_ENTITY,
            "{\"Errors\":[{\"code\":\"422.02\",\"title\":\"Participante não autorizado acessar CPF/CNPJ\"}]}");

        assertThatThrownBy(() -> apiClient(fake).get(ROUTE, VARS, Map.of()))
            .isInstanceOf(B3InvestidorException.class)
            .satisfies(e -> {
                B3InvestidorException b = (B3InvestidorException) e;
                assertThat(b.code()).isEqualTo(B3InvestidorException.NOT_AUTHORIZED);
                assertThat(b.isTransient()).isFalse();
                assertThat(b.getMessage()).contains("422.02").contains("{documentNumber}").doesNotContain(CPF);
            });
        assertThat(fake.gets()).hasSize(1);
    }

    @Test
    @DisplayName("Credenciais do Activest recusadas no token: erro claro, sem o segredo")
    void rejectedToken() {
        Fake fake = new Fake()
            .responds(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\",\"error_description\":\"x\"}")
            .responds(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\",\"error_description\":\"x\"}");

        assertThatThrownBy(() -> apiClient(fake).get(ROUTE, VARS, Map.of()))
            .isInstanceOf(B3InvestidorException.class)
            .hasMessageContaining("recusou as credenciais")
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("segredo-ficticio"));
    }

    @Test
    @DisplayName("Paginacao: segue pelo parametro page enquanto Links (ou links) trouxer next")
    void pages() {
        Fake fake = new Fake().token()
            .responds(HttpStatus.OK, "{\"data\":{\"n\":1},\"Links\":{\"next\":\"https://outro.host/x?page=2\"}}")
            .responds(HttpStatus.OK, "{\"data\":{\"n\":2},\"links\":{\"next\":\"https://outro.host/x?page=3\"}}")
            .responds(HttpStatus.OK, "{\"data\":{\"n\":3},\"Links\":{\"next\":null}}");

        List<JsonNode> r = apiClient(fake).getAll(ROUTE, VARS, PERIOD);

        assertThat(r).extracting(p -> p.path("data").path("n").asInt()).containsExactly(1, 2, 3);
        assertThat(fake.gets()).extracting(p -> p.url().getRawQuery()).containsExactly(
            "referenceStartDate=2024-01-01&referenceEndDate=2024-01-31&page=1",
            "referenceStartDate=2024-01-01&referenceEndDate=2024-01-31&page=2",
            "referenceStartDate=2024-01-01&referenceEndDate=2024-01-31&page=3");
        assertThat(fake.gets()).allMatch(p -> p.url().getHost().equals("b3.ficticia.test"));
    }
}
