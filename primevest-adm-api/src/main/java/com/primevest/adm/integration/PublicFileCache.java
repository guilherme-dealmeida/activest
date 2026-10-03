package com.primevest.adm.integration;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Arquivos publicos (CVM, B3) com cache em disco, em {@code carga/data/}
 * (docs/planos/PLANO_EMPRESAS_BALANCOS.md, secao 2).
 *
 * <p><b>O arquivo e o estado.</b> Sem {@code refresh}, o que existe em disco e
 * usado sem ir a rede. Com {@code refresh}, pergunta com
 * {@code If-Modified-Since} e so baixa se mudou. Fonte fora do ar: fica com a
 * copia em disco. Nao ha tabela de controle: apagar a pasta so obriga a baixar
 * de novo.</p>
 *
 * <p>Download direto para arquivo (o COTAHIST passa de 80 MB), num temporario
 * que so vira o arquivo final quando termina — download interrompido nao deixa
 * arquivo pela metade no cache.</p>
 */
@Slf4j
public class PublicFileCache {

    /** A fonte recusou ou nao respondeu, e nao ha copia em disco. */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final HttpClient http;
    private final Duration timeout;

    public PublicFileCache(Duration timeout) {
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * O arquivo de {@code url} em {@code target}.
     *
     * @return vazio quando a fonte responde 404 e nao ha copia em disco
     * @throws UnavailableException fonte fora do ar e sem copia em disco
     */
    public Optional<Path> fetch(URI url, Path target, boolean refresh) {
        boolean exists = Files.isRegularFile(target);
        if (exists && !refresh) {
            return Optional.of(target);
        }
        try {
            Files.createDirectories(target.getParent());
            HttpRequest.Builder req = HttpRequest.newBuilder(url).timeout(timeout).GET();
            if (exists) {
                Instant modified = Files.getLastModifiedTime(target).toInstant();
                req.header("If-Modified-Since",
                        DateTimeFormatter.RFC_1123_DATE_TIME.format(modified.atZone(ZoneId.of("GMT"))));
            }
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            HttpResponse<Path> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofFile(temp));
            int status = resp.statusCode();
            if (status == 304) {
                Files.deleteIfExists(temp);
                return Optional.of(target);
            }
            if (status == 404) {
                Files.deleteIfExists(temp);
                return exists ? Optional.of(target) : Optional.empty();
            }
            if (status != 200) {
                Files.deleteIfExists(temp);
                throw new IOException("HTTP " + status);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            resp.headers().firstValue("Last-Modified").ifPresent(lm -> {
                try {
                    Instant when = ZonedDateTime.parse(lm, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    Files.setLastModifiedTime(target, FileTime.from(when));
                } catch (Exception ignored) {
                    // sem a data da fonte, fica a do download
                }
            });
            log.info("Baixado {} ({} KB)", url, Files.size(target) / 1024);
            return Optional.of(target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("Download interrompido: " + url, e);
        } catch (IOException e) {
            if (exists) {
                log.warn("{} indisponivel ({}), usando a copia em disco", url, e.getMessage());
                return Optional.of(target);
            }
            throw new UnavailableException(url + " indisponivel: " + e.getMessage(), e);
        } catch (UncheckedIOException e) {
            throw new UnavailableException(url + " ilegivel: " + e.getMessage(), e);
        }
    }
}
