package com.activest.adm.integration.massive;

import com.activest.adm.integration.foreign.ForeignQuotesProvider;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.DayQuotes;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.Quote;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.Status;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cliente do Massive grouped daily (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, D5, I1 e I5) contra um servidor HTTP
 * local, com um recorte da resposta real do pregão de 30/10/2026.
 */
class MassiveClientTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);
    /** 01/11/2026 08:00 UTC — em Nova York já é 01/11: o pregão de 30/10 está liberado. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T08:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private HttpServer server;
    private final Deque<Response> responses = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();

    private record Response(int code, String body) {
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requests.incrementAndGet();
            authorization.set(ex.getRequestHeaders().getFirst("Authorization"));
            query.set(ex.getRequestURI().toString());
            Response r = responses.isEmpty() ? new Response(500, "{}") : responses.poll();
            byte[] body = r.body().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(r.code(), body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MassiveClient client(String key) {
        return new MassiveClient("http://127.0.0.1:" + server.getAddress().getPort(), key, dir, CLOCK, 0, 1);
    }

    private static String sample() throws Exception {
        try (InputStream in = MassiveClientTest.class.getResourceAsStream("/massive/grouped_2026-10-30.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Pregão: ticker na grafia do catálogo, volume arredondado, linha sem fechamento fora, arquivo guardado e relido sem rede")
    void day() throws Exception {
        responses.add(new Response(200, sample()));
        MassiveClient client = client("chave");

        DayQuotes q = client.day(DAY);

        assertThat(q.status()).isEqualTo(Status.OK);
        assertThat(q.fromDisk()).isFalse();
        assertThat(authorization.get()).isEqualTo("Bearer chave");
        assertThat(query.get()).isEqualTo("/v2/aggs/grouped/locale/us/market/stocks/2026-10-30?adjusted=false");
        Map<String, Quote> byTicker = q.quotes().stream().collect(Collectors.toMap(Quote::ticker, Function.identity()));
        assertThat(byTicker).containsOnlyKeys("IAU", "AAPL", "BRK.B", "ABR-E", "ZTEST");
        assertThat(byTicker.get("IAU").close()).isEqualByComparingTo("72.92");
        assertThat(byTicker.get("IAU").volume()).isEqualTo(6_971_672L);
        assertThat(byTicker.get("AAPL").volume()).isEqualTo(67_841_555L);
        assertThat(byTicker.get("ABR-E").volume()).isEqualTo(12_034L);
        assertThat(byTicker.get("BRK.B").open()).isEqualByComparingTo(new BigDecimal("540.0"));
        assertThat(Files.isRegularFile(client.file(DAY))).isTrue();
        assertThat(client.file(DAY)).endsWith(Path.of("2026", "2026-10-30.json.gz"));

        DayQuotes again = client.day(DAY);
        assertThat(again.fromDisk()).isTrue();
        assertThat(again.quotes()).hasSize(5);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Resposta vazia: dia sem pregão (I4), nada guardado")
    void noSession() {
        responses.add(new Response(200, "{\"queryCount\":0,\"resultsCount\":0,\"status\":\"OK\"}"));
        MassiveClient client = client("chave");

        assertThat(client.day(DAY).status()).isEqualTo(Status.NO_SESSION);
        assertThat(Files.exists(client.file(DAY))).isFalse();
    }

    @Test
    @DisplayName("403 no dia corrente de Nova York: ainda não liberado; 403 num dia passado: fora do plano (I5)")
    void forbidden() {
        String body = "{\"status\":\"NOT_AUTHORIZED\",\"message\":\"Attempted to request data past historical entitlements.\"}";
        responses.add(new Response(403, body));
        responses.add(new Response(403, body));
        MassiveClient client = client("chave");

        assertThat(client.day(LocalDate.of(2026, 11, 1)).status()).isEqualTo(Status.NOT_RELEASED);
        assertThatThrownBy(() -> client.day(LocalDate.of(2024, 10, 30)))
                .isInstanceOf(ForeignQuotesProvider.OutOfRangeException.class)
                .hasMessageContaining("historical entitlements");
    }

    @Test
    @DisplayName("401 e chave ausente: AuthException; sem chave nem vai à rede")
    void auth() {
        responses.add(new Response(401, "{\"status\":\"ERROR\",\"error\":\"Unknown API Key\"}"));

        assertThatThrownBy(() -> client("errada").day(DAY)).isInstanceOf(ForeignQuotesProvider.AuthException.class);
        assertThatThrownBy(() -> client("").day(DAY)).isInstanceOf(ForeignQuotesProvider.AuthException.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("429: espera e tenta de novo; persistente: fonte indisponível")
    void rateLimit() throws Exception {
        responses.add(new Response(429, "{\"status\":\"ERROR\"}"));
        responses.add(new Response(200, sample()));
        assertThat(client("chave").day(DAY).quotes()).hasSize(5);
        assertThat(requests.get()).isEqualTo(2);

        responses.addAll(List.of(new Response(429, "{}"), new Response(429, "{}"), new Response(429, "{}")));
        assertThatThrownBy(() -> client("chave").day(LocalDate.of(2026, 10, 29)))
                .isInstanceOf(ForeignQuotesProvider.UnavailableException.class)
                .hasMessageContaining("429");
    }

    @Test
    @DisplayName("Grafia do Massive → catálogo: só o p minúsculo da preferencial vira hífen (D5)")
    void catalogTicker() {
        assertThat(MassiveClient.catalogTicker("ABRpE")).isEqualTo("ABR-E");
        assertThat(MassiveClient.catalogTicker("BRK.B")).isEqualTo("BRK.B");
        assertThat(MassiveClient.catalogTicker("SPY")).isEqualTo("SPY");
        assertThat(MassiveClient.catalogTicker("PSApO")).isEqualTo("PSA-O");
    }
}
