package com.primevest.adm.integration.indices;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * Um ano de um indice em arquivo (docs/planos/PLANO_INDICES_MERCADO.md, 4.1 e D3), em {@code carga/data}.
 *
 * <p><b>Ano fechado com arquivo: so disco.</b> Ano a atualizar ({@code refresh} — o corrente e o anterior,
 * que ainda recebe o ultimo mes dos indices mensais): sempre a fonte, e o arquivo e regravado. Fonte fora do
 * ar com arquivo em disco: fica com o disco (log). Sem arquivo: {@link IndexSourceException}.</p>
 *
 * <p>Retentativas com espera crescente: o SGS as vezes demora ou cai (timeout medido em 05/10/2026). 404 e
 * "sem dados no periodo" (o SGS responde assim) e grava o corpo vazio informado, para o ano fechado nao ser
 * perguntado de novo. Resposta que nao passa em {@code valid} nao e gravada.</p>
 */
@Slf4j
public class IndexYearCache {

    private final HttpClient http;
    private final Duration timeout;
    private final int attempts;
    private final long retryBaseMillis;

    public IndexYearCache(Duration timeout, int attempts, long retryBaseMillis) {
        this.timeout = timeout;
        this.attempts = Math.max(1, attempts);
        this.retryBaseMillis = retryBaseMillis;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Conteudo do ano: do disco ou da fonte.
     *
     * @param emptyBody o que gravar quando a fonte responde 404 (sem dados no periodo)
     * @param valid     confere o corpo antes de gravar (JSON do formato esperado)
     */
    public String fetch(URI url, Path target, boolean refresh, String emptyBody, Predicate<String> valid) {
        boolean exists = Files.isRegularFile(target);
        if (exists && !refresh) {
            return read(target);
        }
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder(url).timeout(timeout)
                        .header("Accept", "application/json").GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();
                String body;
                if (status == 404) {
                    body = emptyBody;
                } else if (status == 200 && valid.test(resp.body())) {
                    body = resp.body();
                } else {
                    throw new IOException("HTTP " + status + (status == 200 ? " com corpo inesperado" : ""));
                }
                write(target, body);
                return body;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IndexSourceException("Interrompido: " + url, e);
            } catch (IOException e) {
                last = e;
                if (attempt < attempts) {
                    sleep(retryBaseMillis * (1L << (attempt - 1)));
                }
            }
        }
        if (exists) {
            log.warn("{} indisponivel ({}), usando a copia em disco", url, last.getMessage());
            return read(target);
        }
        throw new IndexSourceException(url + " indisponivel depois de " + attempts + " tentativas: "
                + last.getMessage(), last);
    }

    private static String read(Path target) {
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IndexSourceException("Arquivo ilegivel: " + target, e);
        }
    }

    private static void write(Path target, String body) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            Files.writeString(temp, body, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IndexSourceException("Nao foi possivel gravar " + target, e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexSourceException("Interrompido", e);
        }
    }
}
