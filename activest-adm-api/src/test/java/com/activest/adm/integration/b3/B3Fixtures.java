package com.activest.adm.integration.b3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Respostas reais da B3 e do FNET, gravadas em 24/09/2026 (src/test/resources/b3).
 *
 * <p>Sao o seguro contra o risco da F5: o endpoint da B3 nao e documentado e
 * pode mudar sem aviso. Se o formato mudar, a resposta nova gravada aqui faz o
 * teste quebrar antes de gravar dado errado.</p>
 */
public final class B3Fixtures {

    private B3Fixtures() {
    }

    public static String read(String name) {
        try (InputStream in = B3Fixtures.class.getResourceAsStream("/b3/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Fixture ausente: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Um B3Client que responde com os arquivos gravados, escolhendo pelo
     * endpoint e pelo parametro (o JSON em base64 no fim da URL).
     */
    public static B3Client client() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            String path = request.url().getRawPath();
            // O base64 pode conter '/': o parametro e tudo depois do segmento Get...
            int endpoint = path.indexOf("/Get");
            String param = new String(Base64.getDecoder().decode(path.substring(path.indexOf('/', endpoint + 1) + 1)),
                    StandardCharsets.UTF_8);
            String file;
            if (path.contains("GetListedSupplementCompany")) {
                file = "company_" + field(param, "issuingCompany") + ".json";
            } else if (path.contains("GetListedSupplementFunds")) {
                file = "fund_" + field(param, "identifierFund") + ".json";
            } else if (path.contains("GetDetail")) {
                file = "detail_" + ("9512".equals(field(param, "codeCVM")) ? "PETR" : "TAEE") + ".json";
            } else if (path.contains("GetListedCashDividends")) {
                file = "history_PETR_p1.json";
            } else {
                return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
            }
            if (B3Fixtures.class.getResource("/b3/" + file) == null) {
                // A B3 responde 200 com corpo vazio para emissor que nao conhece.
                return Mono.just(ClientResponse.create(HttpStatus.OK).body("").build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(read(file)).build());
        });
        return new B3Client(builder, "https://sistemaswebb3-listados.b3.com.br", 5, new ObjectMapper());
    }

    private static String field(String json, String name) {
        try {
            return new ObjectMapper().readTree(json).path(name).asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uma linha real do COTAHIST gravado, pelo inicio (ex.: {@code "012026092502PETR4 "}). */
    public static String cotahistLine(String prefix) {
        try {
            java.nio.file.Path txt = java.nio.file.Path.of(
                    B3Fixtures.class.getResource("/b3/cotahist/COTAHIST_A2026.TXT").toURI());
            return java.nio.file.Files.readAllLines(txt, java.nio.charset.Charset.forName("ISO-8859-1")).stream()
                    .filter(l -> l.startsWith(prefix)).findFirst().orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Zip anual do COTAHIST montado com as linhas reais gravadas em 27/09/2026
     * ({@code /b3/cotahist/COTAHIST_A2026.TXT}), com o ano pedido no nome e
     * linhas extras no fim.
     */
    public static java.nio.file.Path cotahistZip(java.nio.file.Path tmp, int year, String... extraLines) {
        try {
            java.nio.file.Path txt = java.nio.file.Path.of(
                    B3Fixtures.class.getResource("/b3/cotahist/COTAHIST_A2026.TXT").toURI());
            java.nio.file.Path target = tmp.resolve("COTAHIST_A" + year + ".ZIP");
            java.nio.charset.Charset latin1 = java.nio.charset.Charset.forName("ISO-8859-1");
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(target);
                 java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
                zip.putNextEntry(new java.util.zip.ZipEntry("COTAHIST_A" + year + ".TXT"));
                java.nio.file.Files.copy(txt, zip);
                for (String line : extraLines) {
                    zip.write((line + "\r\n").getBytes(latin1));
                }
                zip.closeEntry();
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
