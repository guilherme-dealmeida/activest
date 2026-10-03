package com.primevest.integration.binance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.integration.credential.ApiKeyCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.LongSupplier;

/**
 * Cliente HTTP da Binance — so leitura, so GET (docs/planos/PLANO_CRIPTO_BINANCE.md, 5.3).
 *
 * <p><b>Assinatura</b>: HMAC-SHA256 da query string exata, com o segredo do
 * usuario, em hexadecimal, no parametro {@code signature}. A chave vai no
 * cabecalho {@code X-MBX-APIKEY}. A URL e montada a mao e entregue pronta ao
 * WebClient: se ele reescrevesse a codificacao, a assinatura deixaria de bater.</p>
 *
 * <p><b>Relogio</b>: o {@code timestamp} usa o relogio da Binance
 * ({@code /api/v3/time}), sincronizado na primeira chamada. Se ainda assim vier
 * -1021 (relogio fora da janela), sincroniza de novo e tenta UMA vez.</p>
 *
 * <p><b>Nada de segredo em log ou excecao</b>: as mensagens de erro levam so o
 * caminho e o {@code code}/{@code msg} da Binance.</p>
 *
 * <p>Numeros decimais sao lidos como BigDecimal: quantidades de cripto tem 8
 * casas e nao podem passar por double.</p>
 *
 * <p><b>Limite de peso (F5)</b>: a Binance conta o peso por IP, e todos os
 * usuarios saem do mesmo servidor. Cada resposta traz o peso usado no minuto
 * ({@code X-MBX-USED-WEIGHT-1M}); passando de {@code binance.weight-threshold},
 * a proxima chamada espera o minuto virar. Um 429 espera o Retry-After e tenta
 * UMA vez; um 418 (IP banido) sobe como erro. O worker processa uma
 * sincronizacao por vez, entao o orcamento e um so para todos.</p>
 */
@Slf4j
@Component
public class BinanceClient {

    private static final long RECV_WINDOW = 10_000;

    private final WebClient webClient;
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);

    /** Relogio local. Troca nos testes. */
    LongSupplier clock = System::currentTimeMillis;
    /** Pausa entre chamadas pesadas. Troca nos testes para nao dormir. */
    Sleeper sleeper = Thread::sleep;

    /** Diferenca entre o relogio da Binance e o local; nulo = ainda nao sincronizado. */
    private volatile Long timeOffset;

    private static final long MINUTE = 60_000;
    private static final long MAX_RETRY_AFTER_SECONDS = 60;
    /** Peso usado no minuto, pela ultima resposta, e o minuto a que ele se refere. */
    private volatile int usedWeight;
    private volatile long usedWeightMinute = -1;
    private final int weightThreshold;

    public BinanceClient(WebClient.Builder builder, String baseUrl, long timeoutSeconds) {
        this(builder, baseUrl, timeoutSeconds, 4800);
    }

    @Autowired
    public BinanceClient(WebClient.Builder builder,
                         @Value("${binance.base-url:https://api.binance.com}") String baseUrl,
                         @Value("${binance.timeout-seconds:30}") long timeoutSeconds,
                         @Value("${binance.weight-threshold:4800}") int weightThreshold) {
        this.webClient = builder.clone()
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.weightThreshold = weightThreshold;
    }

    /** Peso usado no minuto corrente, pela ultima resposta (0 se o minuto virou). */
    public int usedWeight() {
        return usedWeightMinute == clock.getAsLong() / MINUTE ? usedWeight : 0;
    }

    /** GET publico (sem assinatura), ex.: {@code /api/v3/exchangeInfo}. */
    public JsonNode publicGet(String path, Map<String, ?> params) {
        return send(path, query(params), null);
    }

    /** GET assinado com a credencial do usuario. */
    public JsonNode signedGet(ApiKeyCredentials credentials, String path, Map<String, ?> params) {
        try {
            return signedOnce(credentials, path, params);
        } catch (BinanceException e) {
            if (!e.isClockSkew()) {
                throw e;
            }
            log.warn("Binance -1021 em {}: sincronizando o relogio e tentando de novo", path);
            timeOffset = null;
            return signedOnce(credentials, path, params);
        }
    }

    /** Espera entre janelas de endpoints de peso alto (Convert). */
    public void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BinanceException(0, null, "Consulta a Binance interrompida.");
        }
    }

    private JsonNode signedOnce(ApiKeyCredentials credentials, String path, Map<String, ?> params) {
        Map<String, Object> all = new LinkedHashMap<>(params);
        all.put("recvWindow", RECV_WINDOW);
        all.put("timestamp", serverTime());
        String query = query(all);
        String signed = query + (query.isEmpty() ? "" : "&") + "signature=" + sign(query, credentials.apiSecret());
        return send(path, signed, credentials.apiKey());
    }

    private long serverTime() {
        Long offset = timeOffset;
        if (offset == null) {
            long antes = clock.getAsLong();
            JsonNode time = send("/api/v3/time", "", null);
            long depois = clock.getAsLong();
            offset = time.path("serverTime").asLong() - (antes + depois) / 2;
            timeOffset = offset;
        }
        return clock.getAsLong() + offset;
    }

    /** HMAC-SHA256 em hexadecimal. Publico para o teste conferir contra o exemplo da documentacao. */
    public static String sign(String query, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(query.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar a requisicao.", e);
        }
    }

    static String query(Map<String, ?> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((k, v) -> {
            if (v != null) {
                joiner.add(encode(k) + "=" + encode(String.valueOf(v)));
            }
        });
        return joiner.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private JsonNode send(String path, String query, String apiKey) {
        esperarOrcamento();
        try {
            return sendOnce(path, query, apiKey);
        } catch (BinanceException e) {
            if (e.status() != 429) {
                throw e;
            }
            long espera = Math.min(e.retryAfterSeconds() != null ? e.retryAfterSeconds() : 10, MAX_RETRY_AFTER_SECONDS);
            log.warn("Binance 429 em {}: nova tentativa em {} s", path, espera);
            pause(espera * 1000);
            return sendOnce(path, query, apiKey);
        }
    }

    /** Passou do limiar de peso neste minuto: espera o minuto virar. */
    private void esperarOrcamento() {
        long agora = clock.getAsLong();
        if (usedWeightMinute == agora / MINUTE && usedWeight >= weightThreshold) {
            long espera = MINUTE - (agora % MINUTE) + 1000;
            log.info("Binance: peso {} no minuto (limiar {}); esperando {} ms", usedWeight, weightThreshold, espera);
            pause(espera);
        }
    }

    private JsonNode sendOnce(String path, String query, String apiKey) {
        URI uri = URI.create(baseUrl + path + (query.isEmpty() ? "" : "?" + query));
        try {
            String body = webClient.get()
                    .uri(uri)
                    .headers(h -> {
                        if (apiKey != null) {
                            h.set("X-MBX-APIKEY", apiKey);
                        }
                    })
                    .exchangeToMono(resp -> handle(resp, path))
                    .timeout(timeout)
                    .block();
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (BinanceException e) {
            throw e;
        } catch (Exception e) {
            // Sem a mensagem da causa: ela pode citar a URL, que leva a assinatura.
            throw new BinanceException(0, null, "Falha de rede ao consultar a Binance em " + path
                    + " (" + e.getClass().getSimpleName() + ").");
        }
    }

    private Mono<String> handle(ClientResponse resp, String path) {
        String peso = resp.headers().asHttpHeaders().getFirst("X-MBX-USED-WEIGHT-1M");
        if (peso != null) {
            try {
                usedWeight = Integer.parseInt(peso.trim());
                usedWeightMinute = clock.getAsLong() / MINUTE;
            } catch (NumberFormatException ignored) {
                // cabecalho estranho: segue sem orcamento
            }
        }
        if (resp.statusCode().is2xxSuccessful()) {
            return resp.bodyToMono(String.class).defaultIfEmpty("");
        }
        int status = resp.statusCode().value();
        Long retryAfter = parseLong(resp.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        return resp.bodyToMono(String.class).defaultIfEmpty("")
                .flatMap(body -> Mono.error(toException(status, body, path, retryAfter)));
    }

    private static Long parseLong(String v) {
        try {
            return v == null || v.isBlank() ? null : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BinanceException toException(int status, String body, String path, Long retryAfter) {
        Integer code = null;
        String msg = null;
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node != null && node.isObject()) {
                code = node.has("code") ? node.path("code").asInt() : null;
                msg = node.path("msg").asText(null);
            }
        } catch (Exception ignored) {
            // corpo nao e JSON
        }
        String detalhe = msg != null ? msg : (body.length() > 200 ? body.substring(0, 200) : body);
        return new BinanceException(status, code, "Binance " + status + (code != null ? " (" + code + ")" : "")
                + " em " + path + ": " + detalhe, retryAfter);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
