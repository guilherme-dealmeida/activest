package com.primevest.adm.integration.cvm;

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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cache em disco dos arquivos da CVM: o arquivo e o estado
 * (docs/planos/PLANO_EMPRESAS_BALANCOS.md, secao 2). Servidor HTTP local no lugar da CVM.
 */
class CvmOpenDataClientTest {

    private static final String LAST_MODIFIED = "Sun, 20 Sep 2026 10:23:00 GMT";

    @TempDir
    Path tmp;

    private HttpServer server;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private CvmOpenDataClient client;

    @BeforeEach
    void subirServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String ims = exchange.getRequestHeaders().getFirst("If-Modified-Since");
            requests.add(path + (ims != null ? " IMS" : ""));
            if (path.endsWith("_2019.zip")) {
                exchange.sendResponseHeaders(404, -1);
            } else if (ims != null) {
                exchange.sendResponseHeaders(304, -1);
            } else {
                // conteudo que passa na conferencia de cada arquivo (PublicFileCache.ContentCheck)
                String prefix = path.endsWith(".zip") ? "PK\u0003\u0004" : path.endsWith(".csv") ? "CNPJ_CIA;" : "";
                byte[] body = (prefix + "conteudo de " + path).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Last-Modified", LAST_MODIFIED);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        Clock setembro2026 = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        client = new CvmOpenDataClient("http://127.0.0.1:" + server.getAddress().getPort() + "/dados",
                tmp.resolve("cvm"), Duration.ofSeconds(5), setembro2026);
    }

    @AfterEach
    void derrubarServidor() {
        server.stop(0);
    }

    @Test
    @DisplayName("Ano corrente: baixa uma vez e depois so pergunta se mudou (304 mantem o arquivo)")
    void anoCorrente() throws IOException {
        Path first = client.yearly(CvmDataset.FCA, 2026).orElseThrow();
        assertThat(first).isEqualTo(tmp.resolve("cvm").resolve("fca").resolve("fca_cia_aberta_2026.zip").toAbsolutePath());
        assertThat(Files.readString(first)).contains("fca_cia_aberta_2026.zip");
        // a data do arquivo e a da fonte, para o proximo If-Modified-Since
        assertThat(Files.getLastModifiedTime(first).toInstant()).isEqualTo(Instant.parse("2026-09-20T10:23:00Z"));

        Path second = client.yearly(CvmDataset.FCA, 2026).orElseThrow();
        assertThat(second).isEqualTo(first);
        assertThat(requests).containsExactly(
                "/dados/CIA_ABERTA/DOC/FCA/DADOS/fca_cia_aberta_2026.zip",
                "/dados/CIA_ABERTA/DOC/FCA/DADOS/fca_cia_aberta_2026.zip IMS");
        assertThat(Files.list(first.getParent())).hasSize(1); // nenhum .part sobrando
    }

    @Test
    @DisplayName("Ano fechado ja em disco: nao vai a rede")
    void anoFechado() throws IOException {
        Path local = tmp.resolve("cvm").resolve("dfp").resolve("dfp_cia_aberta_2015.zip");
        Files.createDirectories(local.getParent());
        Files.writeString(local, "local");

        assertThat(client.yearly(CvmDataset.DFP, 2015)).contains(local.toAbsolutePath());
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("Ano que a CVM nao tem: vazio, sem arquivo")
    void anoInexistente() {
        assertThat(client.yearly(CvmDataset.ITR, 2019)).isEmpty();
        assertThat(tmp.resolve("cvm").resolve("itr").resolve("itr_cia_aberta_2019.zip")).doesNotExist();
    }

    @Test
    @DisplayName("Fonte fora do ar: fica com a copia em disco; sem copia, erro claro")
    void foraDoAr() throws IOException {
        Path local = tmp.resolve("cvm").resolve("cad").resolve("cad_cia_aberta.csv");
        Files.createDirectories(local.getParent());
        Files.writeString(local, "copia");
        server.stop(0);

        assertThat(client.companyRegistry()).isEqualTo(local.toAbsolutePath());
        assertThatThrownBy(() -> client.yearly(CvmDataset.FII_MONTHLY, 2026))
                .isInstanceOf(CvmException.class);
    }
}
