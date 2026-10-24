package com.activest.adm.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/planos/PLANO_FONTE_TESOURO.md, E2 (R1, R2, R4, R7): conferencia do conteudo antes de virar arquivo, o que
 * aconteceu na pergunta, uma tentativa por chamada e a identificacao. Servidor HTTP local no lugar da fonte.
 */
class PublicFileCacheTest {

    private static final String CSV = "Tipo Titulo;Data Vencimento;Data Base\r\nTesouro Selic;01/03/2027;15/10/2026\r\n";
    /** A pagina que o Tesouro Transparente devolveu com HTTP 200 em 21/10/2026 (244 bytes). */
    private static final String REJECTED = "<html><head><title>Request Rejected</title></head><body>The requested URL "
            + "was rejected. Please consult with your administrator.<br><br>Your support ID is: <7615440757605199299>"
            + "<br><br><a href='javascript:history.back();'>[Go Back]</body></html>";
    private static final PublicFileCache.ContentCheck TREASURY = PublicFileCache.ContentCheck.csvHeader("Tipo Titulo;");

    @TempDir
    Path tmp;

    private HttpServer server;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> userAgents = Collections.synchronizedList(new ArrayList<>());
    /** Resposta por caminho: status, corpo e cabecalhos extras. */
    private final Map<String, Object[]> responses = new ConcurrentHashMap<>();
    private final PublicFileCache cache = new PublicFileCache(Duration.ofSeconds(5));

    @BeforeEach
    void startUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            requests.add(path + (ex.getRequestHeaders().getFirst("If-Modified-Since") != null ? " IMS" : ""));
            userAgents.add(ex.getRequestHeaders().getFirst("User-Agent"));
            Object[] r = responses.getOrDefault(path, new Object[]{404, "", Map.of()});
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) r[2];
            headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            byte[] body = ((String) r[1]).getBytes(StandardCharsets.ISO_8859_1);
            int status = (Integer) r[0];
            if (status == 304 || status == 404 || body.length == 0) {
                ex.sendResponseHeaders(status, -1);
            } else {
                ex.sendResponseHeaders(status, body.length);
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void takeDown() {
        server.stop(0);
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private void respond(String path, int status, String body, Map<String, String> headers) {
        responses.put(path, new Object[]{status, body, headers});
    }

    private Path diskCopy(String name, String content) throws IOException {
        Path file = tmp.resolve(name);
        Files.writeString(file, content, StandardCharsets.ISO_8859_1);
        return file;
    }

    @Test
    @DisplayName("CSV certo: baixa, guarda a data da fonte e se identifica como Activest (D6)")
    void low() throws IOException {
        respond("/precos.csv", 200, CSV, Map.of("Last-Modified", "Fri, 16 Oct 2026 10:34:14 GMT"));
        Path target = tmp.resolve("precos.csv");

        PublicFileCache.Result r = cache.get(url("/precos.csv"), target, true, TREASURY);

        assertThat(r.outcome()).isEqualTo(PublicFileCache.Outcome.DOWNLOADED);
        assertThat(r.current()).isTrue();
        assertThat(Files.readString(target, StandardCharsets.ISO_8859_1)).isEqualTo(CSV);
        assertThat(Files.getLastModifiedTime(target).toInstant()).isEqualTo(Instant.parse("2026-10-16T10:34:14Z"));
        assertThat(userAgents).containsExactly(PublicFileCache.USER_AGENT);
    }

    @Test
    @DisplayName("HTTP 200 com a pagina de recusa: nao substitui o arquivo em disco e conta como recusa (R1)")
    void rejectionPageWithCopy() throws IOException {
        respond("/precos.csv", 200, REJECTED, Map.of());
        Path target = diskCopy("precos.csv", CSV);

        PublicFileCache.Result r = cache.get(url("/precos.csv"), target, true, TREASURY);

        assertThat(r.outcome()).isEqualTo(PublicFileCache.Outcome.REFUSED);
        assertThat(r.failed()).isTrue();
        assertThat(r.detail()).contains("conteudo reprovado").contains("pagina HTML");
        assertThat(r.file()).isEqualTo(target);
        assertThat(Files.readString(target, StandardCharsets.ISO_8859_1)).isEqualTo(CSV);
        assertThat(Files.list(tmp)).containsExactly(target); // nenhum .part sobrando
        // o atalho de sempre fica com a copia
        assertThat(cache.fetch(url("/precos.csv"), target, true, TREASURY)).contains(target);
    }

    @Test
    @DisplayName("HTTP 200 com a pagina de recusa e sem copia: nada e gravado e o atalho da erro")
    void rejectionPageWithoutCopy() throws IOException {
        respond("/precos.csv", 200, REJECTED, Map.of());
        Path target = tmp.resolve("precos.csv");

        assertThat(cache.get(url("/precos.csv"), target, true, TREASURY).file()).isNull();
        assertThatThrownBy(() -> cache.fetch(url("/precos.csv"), target, true, TREASURY))
                .isInstanceOf(PublicFileCache.UnavailableException.class)
                .hasMessageContaining("conteudo reprovado");
        assertThat(Files.list(tmp)).isEmpty();
    }

    @Test
    @DisplayName("HTTP 429: recusa com o Retry-After, uma chamada so (R4); a copia fica intacta")
    void requestLimit() throws IOException {
        respond("/precos.csv", 429, "<html>Muitas Requisicoes</html>", Map.of("Retry-After", "30"));
        Path target = diskCopy("precos.csv", CSV);

        PublicFileCache.Result r = cache.get(url("/precos.csv"), target, true, TREASURY);

        assertThat(r.outcome()).isEqualTo(PublicFileCache.Outcome.REFUSED);
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.retryAfter()).isEqualTo("30");
        assertThat(r.detail()).isEqualTo("HTTP 429, Retry-After 30");
        assertThat(Files.readString(target, StandardCharsets.ISO_8859_1)).isEqualTo(CSV);
        assertThat(requests).containsExactly("/precos.csv IMS");
    }

    @Test
    @DisplayName("304, 404 e fonte sem resposta")
    void unchangedDoesNotExistWhenDown() throws IOException {
        respond("/precos.csv", 304, "", Map.of());
        Path target = diskCopy("precos.csv", CSV);
        assertThat(cache.get(url("/precos.csv"), target, true, TREASURY).outcome())
                .isEqualTo(PublicFileCache.Outcome.NOT_MODIFIED);

        PublicFileCache.Result nf = cache.get(url("/nada.csv"), tmp.resolve("nada.csv"), true, TREASURY);
        assertThat(nf.outcome()).isEqualTo(PublicFileCache.Outcome.NOT_FOUND);
        assertThat(cache.fetch(url("/nada.csv"), tmp.resolve("nada.csv"), true, TREASURY)).isEmpty();

        server.stop(0);
        PublicFileCache.Result off = cache.get(url("/precos.csv"), target, true, TREASURY);
        assertThat(off.outcome()).isEqualTo(PublicFileCache.Outcome.UNREACHABLE);
        assertThat(off.file()).isEqualTo(target);
    }

    @Test
    @DisplayName("Sem refresh e com o arquivo em disco: nao vai a rede")
    void fromDisk() throws IOException {
        Path target = diskCopy("precos.csv", CSV);
        assertThat(cache.get(url("/precos.csv"), target, false, TREASURY).outcome())
                .isEqualTo(PublicFileCache.Outcome.FROM_DISK);
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("Conferencias: zip, PDF, JSON, cabecalho do CSV, padrao; vazio nunca passa")
    void reconciliations() throws IOException {
        Path zip = diskCopy("a.zip", "PK\u0003\u0004resto");
        Path pdf = diskCopy("a.pdf", "%PDF-1.7 resto");
        Path json = diskCopy("a.json", "\u00ef\u00bb\u00bf  {\"value\": []}"); // BOM do UTF-8
        Path html = diskCopy("a.html", "\r\n\r\n<!DOCTYPE html>\r\n<html><head>");
        Path empty = diskCopy("vazio", "");
        Path csv = diskCopy("a.csv", CSV);

        assertThat(PublicFileCache.ContentCheck.zip().problem(zip)).isNull();
        assertThat(PublicFileCache.ContentCheck.zip().problem(html)).isEqualTo("pagina HTML");
        assertThat(PublicFileCache.ContentCheck.zip().problem(csv)).isEqualTo("nao e um zip");
        assertThat(PublicFileCache.ContentCheck.pdf().problem(pdf)).isNull();
        // as paginas do RAD da CVM gravadas como PDF em 05/10/2026
        assertThat(PublicFileCache.ContentCheck.pdf().problem(html)).isEqualTo("pagina HTML");
        assertThat(PublicFileCache.ContentCheck.json().problem(json)).isNull();
        assertThat(PublicFileCache.ContentCheck.json().problem(html)).isEqualTo("pagina HTML");
        assertThat(TREASURY.problem(csv)).isNull();
        assertThat(PublicFileCache.ContentCheck.csvHeader("CNPJ_CIA;").problem(csv)).startsWith("cabecalho diferente");
        assertThat(PublicFileCache.ContentCheck.notHtml().problem(csv)).isNull();
        assertThat(PublicFileCache.ContentCheck.notHtml().problem(html)).isEqualTo("pagina HTML");
        for (PublicFileCache.ContentCheck c : List.of(PublicFileCache.ContentCheck.zip(),
                PublicFileCache.ContentCheck.pdf(), PublicFileCache.ContentCheck.json(), TREASURY,
                PublicFileCache.ContentCheck.notHtml())) {
            assertThat(c.problem(empty)).isEqualTo("resposta vazia");
        }
    }
}
