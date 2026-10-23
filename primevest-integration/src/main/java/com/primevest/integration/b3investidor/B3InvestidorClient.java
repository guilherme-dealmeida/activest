package com.primevest.integration.b3investidor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.LongSupplier;

/**
 * Cliente HTTP da API da Area do Investidor da B3 — so leitura, so GET
 * (docs/planos/PLANO_INTEGRACAO_B3.md, 6.1 e 6.3).
 *
 * <p><b>Token</b>: OAuth2 {@code client_credentials} no {@code b3i.token-url} (Microsoft Entra na B3
 * real), com as credenciais do Pacote de Acesso. Vale 3.599 s; fica em cache e e renovado
 * {@code b3i.token-renew-seconds} antes de vencer (A7). Um 401 da API descarta o token e tenta UMA vez.</p>
 *
 * <p><b>mTLS</b>: com {@code b3i.cert-p12-path}, o certificado do Pacote de Acesso vai em toda conexao.</p>
 *
 * <p><b>Paginacao</b>: 20 itens por pagina; segue pelo parametro {@code page} enquanto o objeto
 * {@code Links} (ou {@code links}, A3) trouxer {@code next}. O link em si nao e seguido: a rota e a
 * configurada, e o host do link pode nao ser alcancavel (simulador atras do Docker).</p>
 *
 * <p><b>Falhas</b>: 429 espera o Retry-After (ate 60 s) e tenta UMA vez (A8); 500/502/503/504 e falha de
 * rede tentam UMA vez depois de uma pausa. Erros de negocio (422.01 a 422.07) sobem com o codigo.</p>
 *
 * <p><b>Nada de dado sensivel em log ou excecao</b>: as mensagens levam a rota configurada, com
 * {@code {documentNumber}} no lugar do CPF.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "b3i", name = "enabled", havingValue = "true")
public class B3InvestidorClient {

    static final int MAX_PAGES = 5_000;
    private static final long MAX_RETRY_AFTER_SECONDS = 60;
    private static final long FAILURE_PAUSE_MILLIS = 2_000;

    private final WebClient webClient;
    private final B3InvestidorProperties props;
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    /** Relogio. Troca nos testes. */
    LongSupplier clock = System::currentTimeMillis;
    /** Pausa antes de tentar de novo. Troca nos testes para nao dormir. */
    Sleeper sleeper = Thread::sleep;

    private String token;
    private long tokenExpiresAt;

    public B3InvestidorClient(WebClient.Builder builder, B3InvestidorProperties props) {
        WebClient.Builder b = builder.clone()
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
        if (props.getCertP12Path() != null && !props.getCertP12Path().isBlank()) {
            SslContext ssl = sslContext(props.getCertP12Path(), props.getCertP12Password());
            b.clientConnector(new ReactorClientHttpConnector(HttpClient.create().secure(s -> s.sslContext(ssl))));
            log.info("B3: mTLS com o certificado do Pacote de Acesso");
        }
        this.webClient = b.build();
        this.props = props;
        this.baseUrl = props.getBaseUrl() == null ? "" : props.getBaseUrl().replaceAll("/+$", "");
        this.timeout = Duration.ofSeconds(props.getTimeoutSeconds());
    }

    /** Uma resposta (a primeira pagina, se a rota for paginada). */
    public JsonNode get(String template, Map<String, String> vars, Map<String, ?> query) {
        try {
            return getOnce(template, vars, query);
        } catch (B3InvestidorException e) {
            if (e.status() == 401) {
                log.info("B3 401 em {}: token descartado, nova tentativa", template);
                invalidateToken();
                return getOnce(template, vars, query);
            }
            if (!e.isTransient()) {
                throw e;
            }
            long wait = e.status() == 429
                ? Math.min(e.retryAfterSeconds() != null ? e.retryAfterSeconds() : 5, MAX_RETRY_AFTER_SECONDS) * 1000
                : FAILURE_PAUSE_MILLIS;
            log.warn("B3 {} em {}: nova tentativa em {} ms", e.status(), template, wait);
            pause(wait);
            return getOnce(template, vars, query);
        }
    }

    /** Todas as paginas da rota, na ordem. */
    public List<JsonNode> getAll(String template, Map<String, String> vars, Map<String, ?> query) {
        List<JsonNode> pages = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, Object> q = new LinkedHashMap<>(query);
            q.put("page", page);
            JsonNode body = get(template, vars, q);
            pages.add(body);
            if (!hasNext(body)) {
                return pages;
            }
        }
        throw new B3InvestidorException(0, null, "B3: mais de " + MAX_PAGES + " páginas em " + template
            + "; busca interrompida.", null);
    }

    static boolean hasNext(JsonNode body) {
        JsonNode links = body.has("Links") ? body.path("Links") : body.path("links");
        JsonNode next = links.path("next");
        return !next.isMissingNode() && !next.isNull() && !next.asText().isBlank();
    }

    // ------------------------------------------------------------------ token

    private synchronized String token() {
        long now = clock.getAsLong();
        if (token != null && now < tokenExpiresAt - props.getTokenRenewSeconds() * 1000) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("scope", props.getScope());
        String body;
        try {
            body = webClient.post()
                .uri(URI.create(props.getTokenUrl()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(resp -> handle(resp, "token"))
                .timeout(timeout)
                .block();
        } catch (B3InvestidorException e) {
            if (e.status() == 400 || e.status() == 401) {
                throw new B3InvestidorException(e.status(), e.code(), "A B3 recusou as credenciais do PrimeVest "
                    + "(client_id, client_secret ou scope do Pacote de Acesso).", null);
            }
            throw e;
        } catch (Exception e) {
            throw new B3InvestidorException(0, null, "Falha de rede ao pedir o token da B3 ("
                + e.getClass().getSimpleName() + ").", null);
        }
        try {
            JsonNode r = objectMapper.readTree(body);
            String created = r.path("access_token").asText(null);
            if (created == null || created.isBlank()) {
                throw new B3InvestidorException(0, null, "Resposta de token da B3 sem access_token.", null);
            }
            token = created;
            tokenExpiresAt = now + r.path("expires_in").asLong(3599) * 1000;
            return token;
        } catch (B3InvestidorException e) {
            throw e;
        } catch (Exception e) {
            throw new B3InvestidorException(0, null, "Resposta de token da B3 ilegível.", null);
        }
    }

    synchronized void invalidateToken() {
        token = null;
    }

    // ------------------------------------------------------------------ GET

    private JsonNode getOnce(String template, Map<String, String> vars, Map<String, ?> query) {
        String bearer = token();
        URI uri = URI.create(baseUrl + expand(template, vars) + query(query));
        try {
            String body = webClient.get()
                .uri(uri)
                .headers(h -> h.setBearerAuth(bearer))
                .exchangeToMono(resp -> handle(resp, template))
                .timeout(timeout)
                .block();
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (B3InvestidorException e) {
            throw e;
        } catch (Exception e) {
            // Sem a mensagem da causa: ela pode citar a URL, que leva o CPF.
            throw new B3InvestidorException(0, null, "Falha de rede ao consultar a B3 em " + template
                + " (" + e.getClass().getSimpleName() + ").", null);
        }
    }

    static String expand(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> v : vars.entrySet()) {
            out = out.replace("{" + v.getKey() + "}", URLEncoder.encode(v.getValue(), StandardCharsets.UTF_8));
        }
        return out;
    }

    static String query(Map<String, ?> params) {
        StringJoiner joiner = new StringJoiner("&", "?", "").setEmptyValue("");
        params.forEach((k, v) -> {
            if (v != null) {
                joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
            }
        });
        return joiner.toString();
    }

    private Mono<String> handle(ClientResponse resp, String route) {
        if (resp.statusCode().is2xxSuccessful()) {
            return resp.bodyToMono(String.class).defaultIfEmpty("");
        }
        int status = resp.statusCode().value();
        Long retryAfter = parseLong(resp.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        return resp.bodyToMono(String.class).defaultIfEmpty("")
            .flatMap(body -> Mono.error(toException(status, body, route, retryAfter)));
    }

    /** Corpo de erro da B3: {@code errors[]} ou {@code Errors[]} com {@code code}/{@code title} (A3); do token, {@code error}. */
    B3InvestidorException toException(int status, String body, String route, Long retryAfter) {
        String code = null;
        String msg = null;
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node != null && node.isObject()) {
                JsonNode errors = node.has("errors") ? node.path("errors") : node.path("Errors");
                JsonNode first = errors.isArray() && !errors.isEmpty() ? errors.get(0) : node;
                code = text(first, "code");
                msg = firstText(first, "title", "detail", "message", "error_description", "error");
            }
        } catch (Exception ignored) {
            // corpo nao e JSON
        }
        String detail = msg != null ? msg : (body.length() > 200 ? body.substring(0, 200) : body);
        return new B3InvestidorException(status, code, "B3 " + status + (code != null ? " (" + code + ")" : "")
            + " em " + route + ": " + detail, retryAfter);
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String c : fields) {
            String v = text(n, c);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static Long parseLong(String v) {
        try {
            return v == null || v.isBlank() ? null : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new B3InvestidorException(0, null, "Consulta à B3 interrompida.", null);
        }
    }

    private static SslContext sslContext(String p12, String password) {
        char[] pwd = password == null ? new char[0] : password.toCharArray();
        try (InputStream in = Files.newInputStream(Path.of(p12))) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, pwd);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pwd);
            return SslContextBuilder.forClient().keyManager(kmf).build();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível carregar o certificado da B3 (b3i.cert-p12-path): "
                + e.getClass().getSimpleName(), e);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
