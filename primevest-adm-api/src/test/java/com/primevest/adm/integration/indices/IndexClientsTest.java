package com.primevest.adm.integration.indices;

import com.primevest.domain.marketindex.IndexPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Clientes do SGS e da B3 (docs/planos/PLANO_INDICES_MERCADO.md, X2): leitura das respostas reais, cache por ano
 * em disco (D3) e retentativas. Servidor HTTP local no lugar das fontes; "hoje" e 05/10/2026.
 */
class IndexClientsTest {

    @TempDir
    Path tmp;

    private HttpServer server;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    /** Quantas respostas 500 dar antes de responder normalmente. */
    private final AtomicInteger failures = new AtomicInteger();
    private BcbSgsClient sgs;
    private B3IndexClient b3;

    @BeforeEach
    void subirServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String uri = exchange.getRequestURI().toString();
            requests.add(uri);
            String body = null;
            if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            if (uri.contains("bcdata.sgs.12/") && uri.contains("dataInicial=01/01/2024")) {
                body = IndexFixtures.body("sgs_12_2024");
            } else if (uri.contains("bcdata.sgs.433/") && uri.contains("dataInicial=01/01/2024")) {
                body = IndexFixtures.body("sgs_433_2024");
            } else if (uri.contains("bcdata.sgs.226/") && uri.contains("dataInicial=01/01/2024")) {
                body = IndexFixtures.body("sgs_226_2024");
            } else if (uri.contains("bcdata.sgs.999/")) {
                body = "<html>erro</html>";
            } else if (uri.contains("GetPortfolioDay/")) {
                String param = new String(Base64.getDecoder().decode(uri.substring(uri.lastIndexOf('/') + 1)),
                        StandardCharsets.UTF_8);
                if (param.contains("\"year\":\"2024\"")) {
                    body = IndexFixtures.body(param.contains("IFIX") ? "b3_IFIX_2024" : "b3_IBOV_2024");
                }
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, b.length);
                exchange.getResponseBody().write(b);
            }
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Clock hoje = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        IndexYearCache cache = new IndexYearCache(Duration.ofSeconds(5), 3, 1);
        sgs = new BcbSgsClient(base + "/dados/serie", tmp.resolve("bcb/sgs"), cache, hoje);
        b3 = new B3IndexClient(base, tmp.resolve("b3/indices"), cache, hoje);
    }

    @AfterEach
    void pararServidor() {
        server.stop(0);
    }

    @Test
    @DisplayName("CDI de 2024 (serie 12): 253 dias uteis, em ordem, valor em % ao dia")
    void cdi2024() {
        List<IndexPoint> cdi = sgs.year("12", 2024);

        assertThat(cdi).hasSize(253);
        assertThat(cdi.get(0)).isEqualTo(new IndexPoint(LocalDate.of(2024, 1, 2), new java.math.BigDecimal("0.043739")));
        assertThat(cdi.get(252).date()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(requests.get(0)).contains("/dados/serie/bcdata.sgs.12/dados?formato=json&dataInicial=01/01/2024&dataFinal=31/12/2024");
    }

    @Test
    @DisplayName("IPCA mensal datado no dia 1o; TR: um valor por dia de inicio, o do periodo de um mes cheio")
    void mensalETr() {
        List<IndexPoint> ipca = sgs.year("433", 2024);
        assertThat(ipca).hasSize(12);
        assertThat(ipca.get(7)).isEqualTo(new IndexPoint(LocalDate.of(2024, 8, 1), new java.math.BigDecimal("-0.02")));

        // a TR repete o inicio com fins diferentes (01/05 a 31/05 = 0,0512; 01/05 a 01/06 = 0,0870): uma linha por
        // dia, a do mes cheio
        List<IndexPoint> tr = sgs.year("226", 2024);
        assertThat(tr).hasSize(366);
        assertThat(tr.get(0).date()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(tr).filteredOn(p -> p.date().equals(LocalDate.of(2024, 5, 1)))
                .singleElement().extracting(IndexPoint::value)
                .satisfies(v -> assertThat(v).isEqualByComparingTo("0.0870"));
    }

    @Test
    @DisplayName("Ano fechado: a segunda leitura vem do disco, sem ir a fonte")
    void anoFechadoDoDisco() {
        sgs.year("12", 2024);
        List<IndexPoint> deNovo = sgs.year("12", 2024);

        assertThat(deNovo).hasSize(253);
        assertThat(requests).hasSize(1);
        assertThat(tmp.resolve("bcb/sgs/12/2024.json")).exists();
    }

    @Test
    @DisplayName("Ano corrente e anterior sempre vao a fonte; 404 e 'sem valores' e grava []")
    void anoCorrenteSempreNaFonte() throws IOException {
        assertThat(sgs.year("12", 2026)).isEmpty();
        assertThat(sgs.year("12", 2026)).isEmpty();
        assertThat(sgs.year("12", 2025)).isEmpty();

        assertThat(requests).hasSize(3);
        assertThat(Files.readString(tmp.resolve("bcb/sgs/12/2026.json"))).isEqualTo("[]");
    }

    @Test
    @DisplayName("Fonte instavel: 500 duas vezes e a terceira tentativa resolve")
    void retentativas() {
        failures.set(2);

        assertThat(sgs.year("433", 2024)).hasSize(12);
        assertThat(requests).hasSize(3);
    }

    @Test
    @DisplayName("Fonte fora do ar: com arquivo fica com o disco; sem arquivo, erro — e corpo inesperado nao e gravado")
    void foraDoAr() throws IOException {
        Path arquivo = tmp.resolve("bcb/sgs/12/2026.json");
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, "[{\"data\":\"02/01/2026\",\"valor\":\"0.055131\"}]");
        failures.set(10);

        assertThat(sgs.year("12", 2026)).hasSize(1);
        assertThatThrownBy(() -> sgs.year("433", 2026)).isInstanceOf(IndexSourceException.class);

        failures.set(0);
        assertThatThrownBy(() -> sgs.year("999", 2024)).isInstanceOf(IndexSourceException.class);
        assertThat(tmp.resolve("bcb/sgs/999/2024.json")).doesNotExist();
    }

    @Test
    @DisplayName("B3: grade dia x mes vira um ponto por pregao — Ibovespa 2024, 251 pregoes, de 132.696,63 a 120.283,40")
    void ibovespa2024() {
        List<IndexPoint> ibov = b3.year("IBOV", 2024);

        assertThat(ibov).hasSize(251);
        assertThat(ibov.get(0)).isEqualTo(new IndexPoint(LocalDate.of(2024, 1, 2), new java.math.BigDecimal("132696.63")));
        assertThat(ibov.get(250)).isEqualTo(new IndexPoint(LocalDate.of(2024, 12, 30), new java.math.BigDecimal("120283.40")));
        assertThat(b3.year("IFIX", 2024)).hasSize(251);
        assertThat(tmp.resolve("b3/indices/IBOV/2024.json")).exists();
        // ano sem dados (404) vira grade vazia
        assertThat(b3.year("IBOV", 2009)).isEmpty();
    }

    @Test
    @DisplayName("Numero pt-BR da B3")
    void ptBr() {
        assertThat(B3IndexClient.ptBr("132.696,63")).isEqualByComparingTo("132696.63");
        assertThat(B3IndexClient.ptBr("3.116,28")).isEqualByComparingTo("3116.28");
    }
}
