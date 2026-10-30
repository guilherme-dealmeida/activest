package com.activest.adm.integration.foreign;

import com.activest.adm.integration.PublicFileCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arquivos públicos da SEC (EDGAR), com cache em {@code carga/data/sec} (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md,
 * 11.3): tickers com bolsa, índice trimestral de formulários (Form 25), cadastro das empresas (submissions), documentos
 * de um formulário e o cadastro de séries e classes dos fundos.
 *
 * <p><b>Regras da SEC</b> (fair access): {@code User-Agent} com a empresa e um contato — o e-mail do {@code MAIL_FROM}
 * — e no máximo 8 pedidos por segundo. Arquivo em disco não é baixado de novo, salvo quando quem chama pede
 * ({@code refresh}: a lista de tickers da semana, o trimestre corrente).</p>
 */
@Slf4j
@Component
public class SecClient {

    /** Intervalo mínimo entre pedidos à rede: 8 por segundo. */
    static final long PACE_MILLIS = 125;
    private static final Pattern EMAIL = Pattern.compile("[^<>\\s]+@[^<>\\s]+");
    /**
     * Linha do índice: formulário, empresa, CIK, data e caminho. As colunas do cabeçalho não batem com as dos dados (o
     * tipo de formulário ocupa 17 posições nos dados): a linha é lida pelo fim — o caminho, a data e o CIK têm forma
     * fixa; o formulário vai até o primeiro par de espaços (SC 13D tem um só).
     */
    private static final Pattern FORM_LINE = Pattern.compile(
            "^(\\S+(?: \\S+)*?)\\s{2,}(.+?)\\s+(\\d{1,10})\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(edgar/\\S+)\\s*$");

    private final PublicFileCache files;
    private final String wwwBase;
    private final String dataBase;
    private final Path dir;
    private final ObjectMapper mapper = new ObjectMapper();
    private long lastRequest;
    private final String agent;
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30)).followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();

    @Autowired
    public SecClient(@Value("${foreign.sec.www-url:https://www.sec.gov}") String wwwBase,
                     @Value("${foreign.sec.data-url:https://data.sec.gov}") String dataBase,
                     @Value("${foreign.sec.contact:${MAIL_FROM:}}") String contact,
                     @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this(wwwBase, dataBase, Path.of(dataDir, "sec"),
                new PublicFileCache(Duration.ofSeconds(120), userAgent(contact)), userAgent(contact));
    }

    SecClient(String wwwBase, String dataBase, Path dir, PublicFileCache files) {
        this(wwwBase, dataBase, dir, files, userAgent(null));
    }

    SecClient(String wwwBase, String dataBase, Path dir, PublicFileCache files, String agent) {
        this.agent = agent;
        this.wwwBase = strip(wwwBase);
        this.dataBase = strip(dataBase);
        this.dir = dir.toAbsolutePath().normalize();
        this.files = files;
    }

    /** "Activest/1.0 contato@...": o e-mail do MAIL_FROM (sem o nome entre aspas e os sinais). */
    static String userAgent(String contact) {
        Matcher m = EMAIL.matcher(contact == null ? "" : contact);
        return "Activest/1.0 " + (m.find() ? m.group() : "contato@activest.com.br");
    }

    // ------------------------------------------------------------------ arquivos

    /** Tickers com a bolsa ({@code company_tickers_exchange.json}): CIK, nome, ticker, bolsa. */
    public Path companyTickers(boolean refresh) {
        return get(wwwBase + "/files/company_tickers_exchange.json", dir.resolve("company_tickers_exchange.json"),
                refresh, PublicFileCache.ContentCheck.json());
    }

    /** Os tickers, baixados de novo quando o arquivo em disco tem mais de {@code maxAge}. */
    public Path companyTickers(Duration maxAge) {
        Path target = dir.resolve("company_tickers_exchange.json");
        return companyTickers(!Files.isRegularFile(target) || !fresh(target, maxAge));
    }

    /** Índice de formulários do trimestre ({@code full-index/{ano}/QTR{n}/form.idx}). */
    public Path formIndex(int year, int quarter, boolean refresh) {
        return get(wwwBase + "/Archives/edgar/full-index/" + year + "/QTR" + quarter + "/form.idx",
                dir.resolve("full-index").resolve(String.valueOf(year)).resolve("QTR" + quarter).resolve("form.idx"),
                refresh, PublicFileCache.ContentCheck.notHtml());
    }

    /** Cadastro da empresa na SEC ({@code submissions/CIK##########.json}): SIC, nomes, tickers, formulários. */
    public Path submissions(String cik, boolean refresh) {
        String name = "CIK" + cik10(cik) + ".json";
        return get(dataBase + "/submissions/" + name, dir.resolve("submissions").resolve(name), refresh,
                PublicFileCache.ContentCheck.json());
    }

    /** Um Form 25 (ou 25-NSE) da empresa: a prova da saída da bolsa (C7). */
    public record Filing(String form, LocalDate filed, String accession) {
    }

    /** A ficha da empresa na SEC: o que o catálogo usa do cadastro (submissions). */
    public record Profile(String cik, String name, String sic, String sicDescription, List<String> tickers,
                          List<Filing> delistings) {
    }

    /**
     * A ficha da empresa (C4: SIC; C7: Form 25 recentes), em {@code sec/profiles/{cik}.json}. Lida do disco se tiver
     * menos de {@code maxAge}; senão baixa o cadastro, guarda só a ficha e apaga o cadastro (que tem a lista de
     * formulários, centenas de KB por empresa). Sem resposta da SEC: a ficha antiga, se houver.
     */
    public Optional<Profile> profile(String cik, Duration maxAge) {
        Path p = dir.resolve("profiles").resolve(Long.parseLong(cik) + ".json");
        if (Files.isRegularFile(p) && fresh(p, maxAge)) {
            return readProfile(p);
        }
        Path full;
        try {
            full = submissions(cik, true);
        } catch (PublicFileCache.UnavailableException e) {
            log.warn("SEC: sem o cadastro do CIK {}: {}", cik, e.getMessage());
            return Files.isRegularFile(p) ? readProfile(p) : Optional.empty();
        }
        Profile prof = parseProfile(readJson(full));
        try {
            Files.createDirectories(p.getParent());
            ObjectNode node = mapper.createObjectNode();
            node.put("cik", prof.cik());
            node.put("name", prof.name());
            node.put("sic", prof.sic());
            node.put("sicDescription", prof.sicDescription());
            prof.tickers().forEach(node.putArray("tickers")::add);
            ArrayNode forms = node.putArray("delistings");
            for (Filing f : prof.delistings()) {
                forms.addObject().put("form", f.form()).put("filed", f.filed().toString()).put("accession", f.accession());
            }
            mapper.writeValue(p.toFile(), node);
            Files.deleteIfExists(full);
        } catch (IOException e) {
            log.warn("SEC: nao gravou a ficha {}: {}", p, e.getMessage());
        }
        return Optional.of(prof);
    }

    private Optional<Profile> readProfile(Path p) {
        try {
            JsonNode n = mapper.readTree(p.toFile());
            List<String> tickers = new ArrayList<>();
            n.path("tickers").forEach(t -> tickers.add(t.asText()));
            List<Filing> forms = new ArrayList<>();
            n.path("delistings").forEach(f -> forms.add(new Filing(f.path("form").asText(),
                    LocalDate.parse(f.path("filed").asText()), f.path("accession").asText())));
            return Optional.of(new Profile(n.path("cik").asText(), textOrNull(n, "name"), textOrNull(n, "sic"),
                    textOrNull(n, "sicDescription"), tickers, forms));
        } catch (IOException | RuntimeException e) {
            log.warn("SEC: ficha ilegivel {}: {}", p, e.getMessage());
            return Optional.empty();
        }
    }

    /** A ficha a partir do cadastro (submissions): os Form 25 vêm da lista recente de formulários. */
    static Profile parseProfile(JsonNode root) {
        List<String> tickers = new ArrayList<>();
        root.path("tickers").forEach(t -> tickers.add(t.asText()));
        List<Filing> forms = new ArrayList<>();
        JsonNode recent = root.path("filings").path("recent");
        JsonNode form = recent.path("form");
        for (int i = 0; i < form.size(); i++) {
            String f = form.get(i).asText();
            if (f.equals("25") || f.equals("25-NSE")) {
                forms.add(new Filing(f, LocalDate.parse(recent.path("filingDate").get(i).asText()),
                        recent.path("accessionNumber").get(i).asText()));
            }
        }
        String cik = root.path("cik").asText();
        return new Profile(cik.isEmpty() ? null : String.valueOf(Long.parseLong(cik)), textOrNull(root, "name"),
                textOrNull(root, "sic"), textOrNull(root, "sicDescription"), tickers, forms);
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static boolean fresh(Path f, Duration maxAge) {
        if (maxAge == null) {
            return true;
        }
        try {
            return Files.getLastModifiedTime(f).toInstant().isAfter(java.time.Instant.now().minus(maxAge));
        } catch (IOException e) {
            return false;
        }
    }

    /** Um documento de um formulário (texto do Form 25, 10-K com XBRL inline...): guardado para sempre. */
    public Optional<Path> filingDocument(String cik, String accession, String fileName) {
        String acc = accession.replace("-", "");
        String c = String.valueOf(Long.parseLong(cik));
        pace(dir.resolve("filings").resolve(c).resolve(acc).resolve(fileName));
        return files.fetch(URI.create(wwwBase + "/Archives/edgar/data/" + c + "/" + acc + "/" + fileName),
                dir.resolve("filings").resolve(c).resolve(acc).resolve(fileName), false,
                PublicFileCache.ContentCheck.nonEmpty());
    }

    /** Arquivo completo de um formulário pelo caminho do índice ({@code edgar/data/{cik}/{acesso}.txt}). */
    public Optional<Path> filingByIndexPath(String indexPath) {
        Path target = dir.resolve("filings").resolve(indexPath.replace("edgar/data/", ""));
        pace(target);
        return files.fetch(URI.create(wwwBase + "/Archives/" + indexPath), target, false,
                PublicFileCache.ContentCheck.nonEmpty());
    }

    /** Séries e classes dos fundos registrados no ano ({@code investment-company-series-class-{ano}.csv}). */
    public Optional<Path> seriesClass(int year, boolean refresh) {
        String name = "investment-company-series-class-" + year + ".csv";
        Path target = dir.resolve("funds").resolve(name);
        pace(refresh ? null : target);
        return files.fetch(URI.create(wwwBase + "/files/investment/data/other/investment-company-series-class-information/"
                + name), target, refresh, PublicFileCache.ContentCheck.notHtml());
    }

    /** As séries e classes do ano, baixadas de novo quando o arquivo em disco tem mais de {@code maxAge}. */
    public Optional<Path> seriesClass(int year, Duration maxAge) {
        Path target = dir.resolve("funds").resolve("investment-company-series-class-" + year + ".csv");
        return seriesClass(year, !Files.isRegularFile(target) || !fresh(target, maxAge));
    }

    private Path get(String url, Path target, boolean refresh, PublicFileCache.ContentCheck check) {
        pace(refresh ? null : target);
        return files.fetch(URI.create(url), target, refresh, check)
                .orElseThrow(() -> new PublicFileCache.UnavailableException("SEC sem " + url, null));
    }

    /** Ritmo da SEC: só espera quando o pedido vai à rede (arquivo ausente ou {@code refresh}). */
    private synchronized void pace(Path cached) {
        if (cached != null && Files.isRegularFile(cached)) {
            return;
        }
        long wait = lastRequest + PACE_MILLIS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequest = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ leitura

    /** Uma linha de {@code company_tickers_exchange.json}. {@code exchange}: Nasdaq, NYSE, CBOE, OTC... */
    public record Ticker(String cik, String name, String ticker, String exchange) {
    }

    public List<Ticker> parseCompanyTickers(Path file) {
        try {
            JsonNode root = mapper.readTree(file.toFile());
            Map<String, Integer> col = new HashMap<>();
            JsonNode fields = root.path("fields");
            for (int i = 0; i < fields.size(); i++) {
                col.put(fields.get(i).asText(), i);
            }
            List<Ticker> out = new ArrayList<>();
            for (JsonNode row : root.path("data")) {
                out.add(new Ticker(text(row, col.get("cik")), text(row, col.get("name")), text(row, col.get("ticker")),
                        text(row, col.get("exchange"))));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** O cadastro da empresa (submissions), lido. */
    public JsonNode readJson(Path file) {
        try {
            return mapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(JsonNode row, Integer i) {
        if (i == null || row.get(i) == null || row.get(i).isNull()) {
            return null;
        }
        return row.get(i).asText();
    }

    /** Uma linha do índice de formulários. {@code path}: {@code edgar/data/{cik}/{acesso}.txt}. */
    public record FormEntry(String formType, String company, String cik, LocalDate filed, String path) {

        /** O número de acesso ({@code 0001171843-24-001563}). */
        public String accession() {
            String f = path.substring(path.lastIndexOf('/') + 1);
            return f.endsWith(".txt") ? f.substring(0, f.length() - 4) : f;
        }
    }

    /**
     * As linhas do índice dos formulários pedidos ({@link #FORM_LINE}).
     */
    public static List<FormEntry> parseFormIndex(Path file, Set<String> formTypes) {
        List<FormEntry> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            boolean data = false;
            while ((line = r.readLine()) != null) {
                if (!data) {
                    data = line.startsWith("----");
                    continue;
                }
                Matcher m = FORM_LINE.matcher(line);
                if (!m.matches() || !formTypes.contains(m.group(1).trim())) {
                    continue;
                }
                out.add(new FormEntry(m.group(1).trim(), m.group(2).trim(), m.group(3),
                        LocalDate.parse(m.group(4)), m.group(5)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Uma classe de fundo do cadastro de séries e classes. */
    public record FundClass(String cik, String entityName, String seriesId, String seriesName, String classId,
                            String className, String ticker) {
    }

    /** O cadastro de séries e classes (CSV com aspas; BOM ignorado). Só as classes com ticker. */
    public static List<FundClass> parseSeriesClass(Path file) {
        List<FundClass> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            if (header == null) {
                return out;
            }
            List<String> h = Csv.split(header.replace("\uFEFF", ""), ',');
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < h.size(); i++) {
                col.put(h.get(i).trim(), i);
            }
            String line;
            while ((line = r.readLine()) != null) {
                List<String> v = Csv.split(line, ',');
                String ticker = Csv.get(v, col.get("Class Ticker"));
                if (ticker == null) {
                    continue;
                }
                String cik = Csv.get(v, col.get("CIK Number"));
                out.add(new FundClass(cik == null ? null : String.valueOf(Long.parseLong(cik)),
                        Csv.get(v, col.get("Entity Name")), Csv.get(v, col.get("Series ID")),
                        Csv.get(v, col.get("Series Name")), Csv.get(v, col.get("Class ID")),
                        Csv.get(v, col.get("Class Name")), ticker.trim().toUpperCase()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ deslistados (C6)

    /** Formulários de saída de bolsa (C6, C7). */
    public static final Set<String> FORM_25 = Set.of("25", "25-NSE");

    /**
     * Os Form 25 e 25-NSE do trimestre (C6), guardados só eles em {@code sec/form25/{ano}QTR{n}.idx}: o índice completo
     * (dezenas de MB) é baixado, filtrado e apagado. Trimestre corrente e o anterior são baixados de novo depois de 6
     * dias (o índice cresce até o fim do trimestre); os outros, uma vez.
     */
    public List<FormEntry> form25Index(int year, int quarter) {
        Path compact = dir.resolve("form25").resolve(year + "QTR" + quarter + ".idx");
        LocalDate quarterEnd = LocalDate.of(year, quarter * 3, 1).plusMonths(1).minusDays(1);
        boolean open = !LocalDate.now().isAfter(quarterEnd.plusDays(10));
        if (Files.isRegularFile(compact) && (!open || fresh(compact, Duration.ofDays(6)))) {
            try {
                // o compacto começa pela linha de traços do índice: lido pelo mesmo parser
                return parseFormIndex(compact, FORM_25);
            } catch (RuntimeException e) {
                log.warn("SEC: indice compacto ilegivel {}: {}", compact, e.getMessage());
            }
        }
        Path full = formIndex(year, quarter, true);
        try {
            List<String> keep = new ArrayList<>();
            keep.add("-".repeat(20));
            try (BufferedReader r = Files.newBufferedReader(full, StandardCharsets.ISO_8859_1)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("25 ") || line.startsWith("25-NSE ")) {
                        keep.add(line);
                    }
                }
            }
            Files.createDirectories(compact.getParent());
            Files.write(compact, keep, StandardCharsets.ISO_8859_1);
            Files.deleteIfExists(full);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parseFormIndex(compact, FORM_25);
    }

    /**
     * Texto de um endereço da SEC sem guardar em disco, lendo no máximo {@code maxBytes} (o resto não é baixado):
     * cadastro da empresa, índice de um formulário, o começo de um 10-K. Vazio se a SEC não responder 200.
     */
    public Optional<String> readText(String url, int maxBytes) {
        pace(null);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                java.net.http.HttpResponse<java.io.InputStream> resp = http.send(java.net.http.HttpRequest.newBuilder(
                                URI.create(url)).timeout(Duration.ofSeconds(60)).header("User-Agent", agent)
                        .header("Accept-Encoding", "identity").GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                try (java.io.InputStream in = resp.body()) {
                    if (resp.statusCode() == 429 || resp.statusCode() >= 500) {
                        Thread.sleep(2_000L * attempt);
                        continue;
                    }
                    if (resp.statusCode() != 200) {
                        return Optional.empty();
                    }
                    byte[] b = in.readNBytes(maxBytes);
                    return Optional.of(new String(b, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                log.debug("SEC: {} falhou ({}), tentativa {}", url, e.getMessage(), attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Um endereço do data.sec.gov (página antiga do cadastro: {@code submissions/CIK...-submissions-001.json}). */
    public String dataUrl(String path) {
        return dataBase + "/" + path;
    }

    /** O endereço de um arquivo de um formulário. */
    public String archiveUrl(String cik, String accession, String fileName) {
        return wwwBase + "/Archives/edgar/data/" + Long.parseLong(cik) + "/" + accession.replace("-", "") + "/"
                + fileName;
    }

    /** O cadastro completo da empresa, em memória (lista recente de formulários). */
    public Optional<JsonNode> submissionsJson(String cik) {
        return readText(dataBase + "/submissions/CIK" + cik10(cik) + ".json", 20_000_000).map(t -> {
            try {
                return mapper.readTree(t);
            } catch (IOException e) {
                return null;
            }
        });
    }

    /** CIK com 10 dígitos (a forma dos arquivos da SEC). */
    public static String cik10(String cik) {
        return String.format("%010d", Long.parseLong(cik.trim()));
    }

    private static String strip(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
