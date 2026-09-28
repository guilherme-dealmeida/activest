package com.primevest.adm.integration.b3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.function.Function;

/**
 * Fundos.NET (FNET) — os avisos de rendimentos e amortizacoes publicados pelo
 * administrador de cada FII (PLANO_BRAPI.md, 5.9 e B15; refeito na F8 do
 * PLANO_EMPRESAS_BALANCOS.md, 1.5 e 6.1 — resolve PENDENCIAS 14.1).
 *
 * <p>E a fonte dos proventos de FII alem dos 12 meses que a B3 devolve: o
 * "Aviso aos Cotistas - Estruturado" (categoria 14, tipo 41) e um XML com
 * CNPJ do fundo, ISIN, ticker, data-base (a data-com), valor por cota, data de
 * pagamento, periodo de referencia e se e isento de IR. Existe de 2016 em diante.</p>
 *
 * <p><b>Cache em disco:</b> cada aviso fica em {@code data/fnet/{id}.xml} e nunca e
 * baixado de novo (o aviso e um documento; reapresentacao sai com outro id).
 * <b>Download:</b> o FNET responde em ~0,1 s ou trava — timeout curto (5 s) e ate 6
 * tentativas com espera crescente, no maximo 3 downloads ao mesmo tempo, num executor
 * proprio (nada de uma thread por aviso nem {@code synchronized}). A <b>busca</b> por CNPJ e
 * mais lenta (3 a 10 s, as vezes trava; na carga de 28/09 respondeu 520): timeout proprio de
 * 20 s e 3 tentativas.</p>
 *
 * <p><b>Indice local</b> ({@link #cachedNoticeIds}): cada XML traz o {@code CNPJFundo}, entao
 * a pasta {@code data/fnet} ja responde "quais avisos sao deste fundo" sem perguntar ao FNET.
 * E o que a carga do historico usa; a busca no FNET ({@link #incomeNoticeIds}) fica para
 * trazer avisos novos.</p>
 *
 * <p><b>Dois formatos de XML</b> (os dois no cache, ~metade cada): o atual, com
 * {@code Provento/Rendimento/ValorProvento} e ISIN e ticker por provento; e o antigo
 * (2016 a ~2019), com {@code Rendimento/ValorProventoCota} direto em
 * {@code InformeRendimentos} e ISIN e ticker da cota em {@code DadosGerais}. O cliente
 * antigo so lia o atual: os avisos antigos passavam como "sem valor".</p>
 */
@Slf4j
@Component
public class FnetClient {

    private static final int PAGE = 100;
    private static final Pattern CNPJ_TAG = Pattern.compile("<CNPJFundo>\\s*([^<]*?)\\s*</CNPJFundo>");
    private static final Pattern XML_FILE = Pattern.compile("(\\d+)\\.xml");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final int attempts;
    private final Duration searchTimeout;
    private final int searchAttempts;
    private final long retryBaseMillis;
    private final Path cacheDir;
    private final ExecutorService downloads;
    /** CNPJ -> ids dos avisos em disco. Montado na primeira consulta; download novo entra nele. */
    private volatile Map<String, Set<Long>> index;

    @Autowired
    public FnetClient(WebClient.Builder builder,
                      @Value("${fnet.base-url:https://fnet.bmfbovespa.com.br/fnet/publico}") String baseUrl,
                      @Value("${fnet.timeout-seconds:5}") long timeoutSeconds,
                      @Value("${fnet.attempts:6}") int attempts,
                      @Value("${fnet.retry-base-millis:500}") long retryBaseMillis,
                      @Value("${fnet.parallel-downloads:3}") int parallel,
                      @Value("${fnet.search-timeout-seconds:20}") long searchTimeoutSeconds,
                      @Value("${fnet.search-attempts:3}") int searchAttempts,
                      @Value("${primevest.data.dir:../data}") String dataDir,
                      ObjectMapper objectMapper) {
        this.webClient = builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.attempts = Math.max(1, attempts);
        this.searchTimeout = Duration.ofSeconds(searchTimeoutSeconds);
        this.searchAttempts = Math.max(1, searchAttempts);
        this.retryBaseMillis = Math.max(0, retryBaseMillis);
        this.cacheDir = Path.of(dataDir, "fnet").toAbsolutePath().normalize();
        AtomicInteger n = new AtomicInteger();
        this.downloads = Executors.newFixedThreadPool(Math.max(1, parallel), r -> {
            Thread t = new Thread(r, "fnet-download-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        downloads.shutdownNow();
    }

    /** Pasta do cache ({@code data/fnet}). */
    public Path cacheDir() {
        return cacheDir;
    }

    /**
     * Resultado de uma leitura em lote: os avisos lidos (do cache ou baixados agora),
     * os que nao vieram (ficam para a proxima rotina) e os que vieram mas nao sao XML
     * legivel (ficam no cache e nao sao baixados de novo).
     */
    public record Batch(Map<Long, FnetNotice> notices, Set<Long> failed, Set<Long> unreadable, int downloaded) {
    }

    /** Ids dos avisos de rendimentos e amortizacoes de um fundo, todas as paginas. */
    public List<Long> incomeNoticeIds(String cnpj) {
        List<Long> ids = new ArrayList<>();
        for (int start = 0; start < 5000; start += PAGE) {
            final int s = start;
            String body = getWithRetry(uri -> uri.path("/pesquisarGerenciadorDocumentosDados")
                    .queryParam("d", 1)
                    .queryParam("s", s)
                    .queryParam("l", PAGE)
                    .queryParam("cnpjFundo", cnpj)
                    .queryParam("idCategoriaDocumento", 14)
                    .queryParam("idTipoDocumento", 41)
                    .queryParam("idEspecieDocumento", 0)
                    .build(), "busca de " + cnpj, searchTimeout, searchAttempts);
            JsonNode data;
            try {
                data = objectMapper.readTree(body).path("data");
            } catch (Exception e) {
                throw new B3Exception("FNET respondeu fora do formato esperado: " + e.getMessage(), e);
            }
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            data.forEach(d -> {
                if (d.hasNonNull("id")) {
                    ids.add(d.get("id").asLong());
                }
            });
            if (data.size() < PAGE) {
                break;
            }
        }
        return ids;
    }

    /**
     * Ids dos avisos de um fundo que estao em {@code data/fnet}, em ordem, sem ir ao FNET.
     * Vazio quando nao ha aviso do CNPJ em disco.
     */
    public List<Long> cachedNoticeIds(String cnpj) {
        Set<Long> ids = index().get(cnpj(cnpj));
        return ids == null ? List.of() : ids.stream().sorted().toList();
    }

    /** Relê a pasta {@code data/fnet} (arquivos copiados por fora da aplicacao). */
    public synchronized int reindex() {
        Map<String, Set<Long>> built = new ConcurrentHashMap<>();
        int files = 0;
        if (Files.isDirectory(cacheDir)) {
            try (Stream<Path> list = Files.list(cacheDir)) {
                for (Path file : (Iterable<Path>) list::iterator) {
                    Matcher name = XML_FILE.matcher(file.getFileName().toString());
                    if (!name.matches()) {
                        continue;
                    }
                    String cnpj = cnpjOf(file);
                    if (cnpj != null) {
                        built.computeIfAbsent(cnpj, k -> ConcurrentHashMap.newKeySet()).add(Long.valueOf(name.group(1)));
                        files++;
                    }
                }
            } catch (IOException e) {
                log.warn("FNET: pasta {} ilegivel: {}", cacheDir, e.getMessage());
            }
        }
        index = built;
        log.info("FNET: indice de {} — {} avisos de {} fundos", cacheDir, files, built.size());
        return files;
    }

    private Map<String, Set<Long>> index() {
        Map<String, Set<Long>> current = index;
        if (current == null) {
            reindex();
            current = index;
        }
        return current;
    }

    private static String cnpjOf(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            Matcher m = CNPJ_TAG.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
            return m.find() ? cnpj(m.group(1)) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Os avisos pedidos: o que esta em {@code data/fnet} e lido do disco; o resto e
     * baixado, no maximo {@code fnet.parallel-downloads} ao mesmo tempo, e gravado.
     */
    public Batch incomeNotices(Collection<Long> ids) {
        Map<Long, FnetNotice> notices = new LinkedHashMap<>();
        Set<Long> failed = new LinkedHashSet<>();
        Set<Long> unreadable = new LinkedHashSet<>();
        Map<Long, Future<String>> pending = new LinkedHashMap<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            Path file = cacheFile(id);
            if (Files.isRegularFile(file)) {
                readCached(id, file, notices, unreadable);
            } else {
                pending.put(id, downloads.submit(() -> download(id)));
            }
        }
        int downloaded = 0;
        for (Map.Entry<Long, Future<String>> e : pending.entrySet()) {
            long id = e.getKey();
            String xml;
            try {
                xml = e.getValue().get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                failed.add(id);
                continue;
            } catch (ExecutionException ee) {
                log.debug("FNET: aviso {} nao obtido: {}", id, ee.getCause().getMessage());
                failed.add(id);
                continue;
            }
            downloaded++;
            try {
                FnetNotice notice = parseNotice(xml);
                notices.put(id, notice);
                Map<String, Set<Long>> current = index;
                if (current != null && notice.fundCnpj() != null) {
                    current.computeIfAbsent(notice.fundCnpj(), k -> ConcurrentHashMap.newKeySet()).add(id);
                }
            } catch (B3Exception pe) {
                log.warn("FNET: aviso {} baixado mas ilegivel ({}) — fica em {} para conferencia", id,
                        pe.getMessage(), cacheFile(id));
                unreadable.add(id);
            }
        }
        return new Batch(notices, failed, unreadable, downloaded);
    }

    private void readCached(long id, Path file, Map<Long, FnetNotice> notices, Set<Long> unreadable) {
        String xml;
        try {
            xml = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // gravado por outra ferramenta em outra codificacao
            try {
                xml = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            } catch (IOException e2) {
                unreadable.add(id);
                return;
            }
        }
        try {
            notices.put(id, parseNotice(xml));
        } catch (B3Exception e) {
            unreadable.add(id);
        }
    }

    /** Baixa com tentativas e grava no cache (mesmo que nao seja legivel: nao baixa de novo). */
    private String download(long id) {
        String body = getWithRetry(uri -> uri.path("/downloadDocumento").queryParam("id", id).build(), "aviso " + id,
                timeout, attempts);
        String xml = normalize(body);
        try {
            Files.createDirectories(cacheDir);
            Path tmp = Files.createTempFile(cacheDir, id + "-", ".tmp");
            Files.writeString(tmp, xml, StandardCharsets.UTF_8);
            Files.move(tmp, cacheFile(id), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("FNET: aviso {} nao gravado em {}: {}", id, cacheDir, e.getMessage());
        }
        return xml;
    }

    private Path cacheFile(long id) {
        return cacheDir.resolve(id + ".xml");
    }

    // ------------------------------------------------------------------ leitura

    /** Os proventos de um aviso. Publico para os testes com arquivo real. */
    public static List<FnetProvento> parse(String xml) {
        return parseNotice(xml).proventos();
    }

    /**
     * Le o aviso estruturado, nos dois formatos. Provento sem valor positivo ou sem
     * data-base fica de fora (aviso com o bloco vazio). As regras de negocio — recibo,
     * data-base depois do pagamento, CNPJ de outro fundo — sao do chamador.
     */
    public static FnetNotice parseNotice(String xml) {
        List<FnetProvento> result = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return new FnetNotice(null, result);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(normalize(xml).getBytes(StandardCharsets.UTF_8)));
            Element general = first(doc.getDocumentElement(), "DadosGerais");
            String cnpj = cnpj(general == null ? null : text(general, "CNPJFundo"));
            String quotaIsin = general == null ? null : text(general, "CodISINCota");
            String quotaTicker = general == null ? null : text(general, "CodNegociacaoCota");
            Element informe = first(doc.getDocumentElement(), "InformeRendimentos");
            if (informe == null) {
                return new FnetNotice(cnpj, result);
            }
            for (Element item : children(informe)) {
                if ("Provento".equals(item.getTagName())) {
                    String isin = text(item, "CodISIN");
                    String ticker = text(item, "CodNegociacao");
                    for (Element inner : children(item)) {
                        add(inner, isin, ticker, result);
                    }
                } else {
                    add(item, quotaIsin, quotaTicker, result);
                }
            }
            return new FnetNotice(cnpj, result);
        } catch (Exception e) {
            throw new B3Exception("Aviso do FNET ilegivel: " + e.getMessage(), e);
        }
    }

    private static void add(Element item, String isin, String ticker, List<FnetProvento> out) {
        String tag = item.getTagName().toLowerCase();
        boolean amortization = tag.startsWith("amortiza");
        if (!amortization && !tag.startsWith("rendimento")) {
            return;
        }
        var dataBase = B3Values.date(text(item, "DataBase"));
        var value = B3Values.decimal(firstNonNull(text(item, "ValorProvento"), text(item, "ValorProventoCota")));
        if (value == null || value.signum() <= 0 || dataBase == null) {
            return;
        }
        out.add(new FnetProvento(trim(isin), ticker(ticker), amortization, dataBase, value,
                B3Values.date(text(item, "DataPagamento")), text(item, "PeriodoReferencia"),
                exempt(firstTextContaining(item, "Isent"))));
    }

    /** "Sim"/"true" isento; "Nao"/"false" nao; vazio, sem informacao. */
    static Boolean exempt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        if (v.equals("sim") || v.equals("true")) {
            return true;
        }
        if (v.startsWith("n") || v.equals("false")) {
            return false;
        }
        return null;
    }

    /** O formato antigo traz o ticker da cota com um "B" no fim (WPLZ11B): e o WPLZ11. */
    static String ticker(String raw) {
        String t = trim(raw);
        if (t == null) {
            return null;
        }
        t = t.toUpperCase();
        return t.matches("[A-Z]{4}\\d{2}B") ? t.substring(0, t.length() - 1) : t;
    }

    /** So digitos, 14 posicoes (4 fundos publicam pontuado). */
    static String cnpj(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 14) {
            return null;
        }
        return "0".repeat(14 - digits.length()) + digits;
    }

    /** O download costuma vir como XML; alguns documentos vem em base64 (entre aspas). */
    static String normalize(String body) {
        String clean = body.replace("\uFEFF", "").strip();
        if (!clean.startsWith("<")) {
            clean = new String(java.util.Base64.getMimeDecoder().decode(clean.replace("\"", "")),
                    StandardCharsets.UTF_8).replace("\uFEFF", "").strip();
        }
        return clean;
    }

    private static List<Element> children(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    private static Element first(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String t = nodes.item(0).getTextContent();
        return t == null || t.isBlank() ? null : t.trim();
    }

    private static String firstTextContaining(Element parent, String fragment) {
        for (Element e : children(parent)) {
            if (e.getTagName().contains(fragment)) {
                return e.getTextContent();
            }
        }
        return null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------------ rede

    private String getWithRetry(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri, String what,
                                Duration timeout, int attempts) {
        B3Exception last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return get(uri, timeout);
            } catch (B3Exception e) {
                last = e;
                if (attempt < attempts) {
                    sleep(retryBaseMillis << (attempt - 1));
                }
            }
        }
        throw new B3Exception("FNET: " + what + " sem resposta em " + attempts + " tentativas: "
                + (last == null ? "?" : last.getMessage()), last);
    }

    private String get(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri, Duration timeout) {
        try {
            String body = webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(timeout).block();
            if (body == null || body.isBlank()) {
                throw new B3Exception("FNET respondeu vazio");
            }
            return body;
        } catch (B3Exception e) {
            throw e;
        } catch (Exception e) {
            throw new B3Exception("Falha ao consultar o FNET: " + e.getMessage(), e);
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
