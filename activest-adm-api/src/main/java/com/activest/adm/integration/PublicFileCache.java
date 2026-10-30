package com.activest.adm.integration;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Arquivos publicos (CVM, B3, BCB, Tesouro) com cache em disco, em {@code carga/data/}
 * (docs/planos/PLANO_EMPRESAS_BALANCOS.md, secao 2; docs/planos/PLANO_FONTE_TESOURO.md, E2).
 *
 * <p><b>O arquivo e o estado.</b> Sem {@code refresh}, o que existe em disco e
 * usado sem ir a rede. Com {@code refresh}, pergunta com
 * {@code If-Modified-Since} e so baixa se mudou. Fonte fora do ar: fica com a
 * copia em disco. Nao ha tabela de controle: apagar a pasta so obriga a baixar
 * de novo.</p>
 *
 * <p>Download direto para arquivo (o COTAHIST passa de 80 MB), num temporario
 * que so vira o arquivo final quando termina <b>e passa na conferencia do conteudo</b> ({@link ContentCheck}, R1):
 * a pagina HTML de recusa que o Tesouro devolveu com HTTP 200 em 21/10/2026 nao substitui mais o arquivo. Conteudo
 * reprovado conta como recusa da fonte, e o arquivo anterior fica.</p>
 *
 * <p>Dois jeitos de perguntar: {@link #get} devolve o que aconteceu ({@link Result}: baixou, nao mudou, recusou...,
 * R2) e quem chama decide o que fazer com a copia em disco; {@link #fetch} e o atalho de sempre — recusa ou fonte
 * fora do ar com copia em disco devolvem a copia. Uma tentativa por chamada, sem repetir (R4).</p>
 */
@Slf4j
public class PublicFileCache {

    /**
     * Identificacao honesta nas chamadas (D6 do PLANO_FONTE_TESOURO): quem administra a fonte sabe quem pede. Nunca
     * imitar navegador para passar por filtro.
     */
    public static final String USER_AGENT = "Activest/1.0 (+https://activest.com.br)";

    /** A fonte recusou ou nao respondeu, e nao ha copia em disco. */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** O que aconteceu na pergunta a fonte. */
    public enum Outcome {
        /** Baixou um arquivo novo, que passou na conferencia. */
        DOWNLOADED,
        /** A fonte respondeu 304: o arquivo em disco continua o da fonte. */
        NOT_MODIFIED,
        /** Sem {@code refresh} e com o arquivo em disco: nao foi a rede. */
        FROM_DISK,
        /** A fonte respondeu 404. */
        NOT_FOUND,
        /** A fonte respondeu, mas recusou (HTTP 429, 403, 5xx...) ou mandou outra coisa no lugar do arquivo. */
        REFUSED,
        /** Sem resposta (rede, tempo limite). */
        UNREACHABLE
    }

    /**
     * @param outcome    o que aconteceu
     * @param file       o arquivo em disco depois da pergunta (o novo, ou a copia anterior); nulo se nao ha nenhum
     * @param detail     a explicacao para log e mensagem (ex.: "HTTP 429, Retry-After 30"); nula quando deu certo
     * @param status     o status HTTP da resposta, quando houve resposta
     * @param retryAfter o cabecalho {@code Retry-After} da recusa, se veio
     */
    public record Result(Outcome outcome, Path file, String detail, Integer status, String retryAfter) {

        public Optional<Path> path() {
            return Optional.ofNullable(file);
        }

        /** O arquivo em disco e o da fonte agora (baixou, 304, ou nao precisava perguntar). */
        public boolean current() {
            return outcome == Outcome.DOWNLOADED || outcome == Outcome.NOT_MODIFIED || outcome == Outcome.FROM_DISK;
        }

        /** A fonte recusou ou nao respondeu. */
        public boolean failed() {
            return outcome == Outcome.REFUSED || outcome == Outcome.UNREACHABLE;
        }
    }

    private final HttpClient http;
    private final Duration timeout;
    private final String userAgent;

    public PublicFileCache(Duration timeout) {
        this(timeout, USER_AGENT);
    }

    /**
     * Com outra identificação: a SEC exige "empresa contato" no {@code User-Agent} e recusa (403) quem não se
     * identifica (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, 11.3).
     */
    public PublicFileCache(Duration timeout, String userAgent) {
        this.timeout = timeout;
        this.userAgent = userAgent;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** {@link #fetch(URI, Path, boolean, ContentCheck)} com a conferencia padrao (nao e pagina HTML nem vazio). */
    public Optional<Path> fetch(URI url, Path target, boolean refresh) {
        return fetch(url, target, refresh, ContentCheck.notHtml());
    }

    /**
     * O arquivo de {@code url} em {@code target}.
     *
     * @return vazio quando a fonte responde 404 e nao ha copia em disco
     * @throws UnavailableException fonte fora do ar, ou recusou, e sem copia em disco
     */
    public Optional<Path> fetch(URI url, Path target, boolean refresh, ContentCheck check) {
        Result r = get(url, target, refresh, check);
        switch (r.outcome()) {
            case NOT_FOUND:
                return r.path();
            case REFUSED:
            case UNREACHABLE:
                if (r.file() != null) {
                    log.warn("{} indisponivel ({}), usando a copia em disco", url, r.detail());
                    return r.path();
                }
                throw new UnavailableException(url + " indisponivel: " + r.detail(), null);
            default:
                return r.path();
        }
    }

    /**
     * Pergunta a fonte e conta o que aconteceu, sem decidir pela copia em disco (R2). Download interrompido:
     * {@link UnavailableException}.
     */
    public Result get(URI url, Path target, boolean refresh, ContentCheck check) {
        boolean exists = Files.isRegularFile(target);
        if (exists && !refresh) {
            return new Result(Outcome.FROM_DISK, target, null, null, null);
        }
        Path previous = exists ? target : null;
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            HttpRequest.Builder req = HttpRequest.newBuilder(url).timeout(timeout).header("User-Agent", userAgent)
                    .GET();
            if (exists) {
                Instant modified = Files.getLastModifiedTime(target).toInstant();
                req.header("If-Modified-Since",
                        DateTimeFormatter.RFC_1123_DATE_TIME.format(modified.atZone(ZoneId.of("GMT"))));
            }
            HttpResponse<Path> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofFile(temp));
            int status = resp.statusCode();
            if (status == 304) {
                Files.deleteIfExists(temp);
                return new Result(Outcome.NOT_MODIFIED, target, null, status, null);
            }
            if (status == 404) {
                Files.deleteIfExists(temp);
                return new Result(Outcome.NOT_FOUND, previous, "HTTP 404", status, null);
            }
            if (status != 200) {
                Files.deleteIfExists(temp);
                String retryAfter = resp.headers().firstValue("Retry-After").orElse(null);
                String detail = "HTTP " + status + (retryAfter != null ? ", Retry-After " + retryAfter : "");
                return new Result(Outcome.REFUSED, previous, detail, status, retryAfter);
            }
            String problem = check.problem(temp);
            if (problem != null) {
                String head = head(temp);
                Files.deleteIfExists(temp);
                log.warn("{} respondeu HTTP 200 com conteudo reprovado ({}); o arquivo em disco nao muda. Inicio: {}",
                        url, problem, head);
                return new Result(Outcome.REFUSED, previous, "HTTP 200 com conteudo reprovado: " + problem, status,
                        null);
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
            return new Result(Outcome.DOWNLOADED, target, null, status, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(temp);
            throw new UnavailableException("Download interrompido: " + url, e);
        } catch (IOException e) {
            deleteQuietly(temp);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new Result(Outcome.UNREACHABLE, previous, msg, null, null);
        } catch (UncheckedIOException e) {
            deleteQuietly(temp);
            throw new UnavailableException(url + " ilegivel: " + e.getMessage(), e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // o .part sobra; o proximo download o sobrescreve
        }
    }

    /** Os primeiros bytes, para o log da recusa. */
    private static String head(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] b = in.readNBytes(120);
            return new String(b, StandardCharsets.ISO_8859_1).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "?";
        }
    }

    /**
     * O que o download precisa ser para virar o arquivo do cache (R1). Devolve o problema, ou nulo se passou. Toda
     * conferencia recusa resposta vazia.
     */
    @FunctionalInterface
    public interface ContentCheck {

        String problem(Path file) throws IOException;

        /** Padrao: nao e vazio nem pagina HTML. */
        static ContentCheck notHtml() {
            return file -> {
                byte[] b = start(file);
                if (b.length == 0) {
                    return "resposta vazia";
                }
                return looksHtml(b) ? "pagina HTML" : null;
            };
        }

        /** Qualquer conteudo nao vazio (documento de formulario da SEC, que pode ser HTML). */
        static ContentCheck nonEmpty() {
            return file -> start(file).length == 0 ? "resposta vazia" : null;
        }

        /** CSV cuja primeira linha comeca com {@code headerPrefix} (ASCII; BOM ignorado). */
        static ContentCheck csvHeader(String headerPrefix) {
            return file -> {
                byte[] b = start(file);
                if (b.length == 0) {
                    return "resposta vazia";
                }
                if (looksHtml(b)) {
                    return "pagina HTML";
                }
                String text = new String(b, StandardCharsets.ISO_8859_1);
                if (text.startsWith("\u00ef\u00bb\u00bf")) {
                    text = text.substring(3);
                }
                return text.startsWith(headerPrefix) ? null : "cabecalho diferente de \"" + headerPrefix + "...\"";
            };
        }

        /** Zip (assinatura {@code PK\3\4}, ou {@code PK\5\6} do zip vazio). */
        static ContentCheck zip() {
            return file -> {
                byte[] b = start(file);
                if (b.length == 0) {
                    return "resposta vazia";
                }
                boolean pk = b.length >= 4 && b[0] == 'P' && b[1] == 'K'
                        && ((b[2] == 3 && b[3] == 4) || (b[2] == 5 && b[3] == 6));
                return pk ? null : looksHtml(b) ? "pagina HTML" : "nao e um zip";
            };
        }

        /** PDF (comeca com {@code %PDF}). */
        static ContentCheck pdf() {
            return file -> {
                byte[] b = start(file);
                if (b.length == 0) {
                    return "resposta vazia";
                }
                return new String(b, StandardCharsets.ISO_8859_1).startsWith("%PDF") ? null
                        : looksHtml(b) ? "pagina HTML" : "nao e um PDF";
            };
        }

        /** JSON (objeto ou lista; BOM e espacos iniciais ignorados). */
        static ContentCheck json() {
            return file -> {
                byte[] b = start(file);
                String text = new String(b, StandardCharsets.ISO_8859_1).replace("\u00ef\u00bb\u00bf", "").stripLeading();
                if (text.isEmpty()) {
                    return "resposta vazia";
                }
                char c = text.charAt(0);
                return c == '{' || c == '[' ? null : looksHtml(b) ? "pagina HTML" : "nao e JSON";
            };
        }

        private static byte[] start(Path file) throws IOException {
            try (InputStream in = Files.newInputStream(file)) {
                return in.readNBytes(1024);
            }
        }

        private static boolean looksHtml(byte[] b) {
            String s = new String(b, StandardCharsets.ISO_8859_1).stripLeading().toLowerCase(Locale.ROOT);
            return s.startsWith("<!doctype html") || s.startsWith("<html") || s.startsWith("<head")
                    || s.startsWith("<body") || (s.startsWith("<") && s.contains("<html"));
        }
    }
}
