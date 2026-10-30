package com.activest.adm.domain.foreign;

import com.activest.adm.integration.foreign.NareitClient;
import com.activest.adm.integration.foreign.SecClient;
import com.activest.domain.asset.AssetTypeNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deslistados dos EUA desde 2010 — a tarefa {@code FOREIGN_DELISTED} (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C6 e
 * D2; etapa F5). Para cada trimestre: os Form 25/25-NSE do índice da SEC → o formulário (classe, bolsa, SIC, data de
 * saída) → só as classes de capital (ação, ADS, units de MLP, cotas de fundo) nas bolsas da D1 → o ticker
 * ({@link DelistedTickers}: XBRL, texto; fundo pelo próprio Form 25 ou pelas séries da SEC; REIT pela Nareit) → tipo
 * (fundo = ETF; SIC 6798 e Nareit do ano = REIT; resto STOCK) e categoria (as regras da C4).
 *
 * <p>Toda rodada grava a prévia {@code levantamento/internacional/deslistados-previa.json}; com {@code WRITE}, cadastra
 * os papéis com ticker como <b>inativos</b>, com a data de saída e a prova {@code FORM_25}. A curadoria
 * ({@code curadoria-deslistados.json}, por número de acesso do Form 25) dá ticker, tipo e categoria ou exclui. O que não
 * fecha fica na planilha e vira a pendência da D2. A empresa que ainda está no catálogo vivo (troca de bolsa) e a saída
 * anterior da mesma empresa ficam fora.</p>
 */
@Slf4j
@Service
public class ForeignDelistedService {

    static final int FIRST_YEAR = 2010;
    /** Ticker não achado é tentado de novo depois de tantos dias. */
    static final int RETRY_DAYS = 180;
    static final String CURATION = "CURATION";
    /** Pedidos simultâneos à SEC: o tempo é de espera da rede; o ritmo (8/s) é do cliente. */
    static final int THREADS = 6;

    private static final Pattern EXCHANGE_FILER = Pattern.compile(
            "STOCK EXCHANGE|STOCK MARKET|\\bNYSE\\b|CBOE|\\bBATS\\b|NASDAQ|\\bARCA\\b|INVESTORS EXCHANGE",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FUND = Pattern.compile(
            "\\bETF\\b|\\bETFs\\b|\\bfund\\b|exchange[- ]traded|\\bindex shares\\b|\\bETN\\b|\\bportfolio\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMON = Pattern.compile(
            "common|ordinary|american depositary|american depository|\\bADS\\b|\\bADR\\b|limited partner|"
                    + "class [a-z] (shares|stock)|shares of beneficial interest|\\bcapital stock\\b|^\\s*shares?\\s*$|"
                    + "units representing", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_EQUITY_ONLY = Pattern.compile(
            "\\bnotes?\\b|debenture|\\bbonds?\\b|preferred|preference|trust securities", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARENTHESES_TICKER = Pattern.compile("\\(\\s*([A-Z]{1,5}(?:\\.[A-Z])?)\\s*\\)\\s*$");

    private final SecClient sec;
    private final NareitClient nareit;
    private final SicCategories sic;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Path dir;
    private final Path cacheDir;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final DelistedTickers tickers;

    @Autowired
    public ForeignDelistedService(SecClient sec, NareitClient nareit, SicCategories sic, JdbcTemplate jdbc,
                                  PlatformTransactionManager txManager,
                                  @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this.sec = sec;
        this.nareit = nareit;
        this.sic = sic;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.dir = Path.of(dataDir, "levantamento", "internacional").toAbsolutePath().normalize();
        this.cacheDir = Path.of(dataDir, "sec", "delisted").toAbsolutePath().normalize();
        this.tickers = new DelistedTickers(sec);
    }

    // ------------------------------------------------------------------ resultado e prévia

    public record Result(int fromYear, int toYear, int forms, int capital, int withTicker, int created, int existing,
                         int withoutCategory, int curated, boolean wrote, Path preview) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d–%d: %d Form 25; %d de capital nas bolsas da D1, %d com ticker "
                            + "(%.0f%%); %s %d; já cadastrados %d; sem categoria %d; curadoria %d; prévia: %s",
                    fromYear, toYear, forms, capital, withTicker, capital == 0 ? 0.0 : 100.0 * withTicker / capital,
                    wrote ? "gravados" : "a gravar (WRITE=false)", created, existing, withoutCategory, curated,
                    preview.getFileName());
        }
    }

    /** Um papel de capital que saiu da bolsa (aba Deslistados ou Sem ticker). */
    public record Row(String accession, String formType, String cik, String company, String filed, String delistedOn,
                      String exchange, String securityClass, String kind, String ticker, String tickerSource,
                      String tickerDocument, String type, String typeSource, String category, String categorySource,
                      String sic, String sicDescription, String nareitSector, String action, Long assetId,
                      String note) {
    }

    /** Form 25 fora do escopo (aba Fora): nota, preferencial, warrant, bolsa fora da D1, troca de bolsa... */
    public record Out(String accession, String cik, String company, String filed, String exchange,
                      String securityClass, String reason) {
    }

    private record Curated(String ticker, String type, String category, boolean exclude) {
    }

    // ------------------------------------------------------------------ a rodada

    public synchronized Result run(Integer fromYear, Integer toYear, boolean write, String reason, Runnable checkStop) {
        LocalDate today = LocalDate.now();
        int from = fromYear == null ? FIRST_YEAR : fromYear;
        int to = toYear == null ? today.getYear() : toYear;
        log.info("Deslistados do exterior ({}): {}–{}, WRITE={}", reason, from, to, write);
        Map<String, Curated> curation = loadCuration();
        Set<String> curatedUsed = new HashSet<>();

        // 1. Os Form 25 do período, um por número de acesso (o 25-NSE aparece também no CIK da bolsa)
        Map<String, SecClient.FormEntry> byAccession = new LinkedHashMap<>();
        for (int y = from; y <= to; y++) {
            for (int q = 1; q <= 4; q++) {
                if (LocalDate.of(y, q * 3 - 2, 1).isAfter(today)) {
                    break;
                }
                for (SecClient.FormEntry e : sec.form25Index(y, q)) {
                    SecClient.FormEntry prev = byAccession.get(e.accession());
                    if (prev == null || EXCHANGE_FILER.matcher(prev.company()).find()) {
                        byAccession.put(e.accession(), e);
                    }
                }
                checkStop.run();
            }
        }

        // 2. O formulário de cada um (pequeno, guardado), lido
        List<Form25> forms = parallel(new ArrayList<>(byAccession.values()), e -> {
            Optional<Path> f = sec.filingByIndexPath(e.path());
            if (f.isEmpty()) {
                return null;
            }
            try {
                return Form25.parse(Files.readString(f.get(), StandardCharsets.ISO_8859_1), e.accession(),
                        e.formType(), e.cik(), e.company(), e.filed());
            } catch (IOException ex) {
                log.warn("Deslistados: {} ilegivel: {}", e.path(), ex.getMessage());
                return null;
            }
        }, checkStop, "Form 25 lidos");
        int n;

        // 3. Só capital nas bolsas da D1; de cada empresa, a última saída de cada espécie; empresa ainda viva fica fora
        Set<String> liveCiks = new HashSet<>(jdbc.queryForList("""
                SELECT f.cik FROM foreign_listings f JOIN assets a ON a.id = f.asset_id
                 WHERE a.active AND f.cik IS NOT NULL""", String.class));
        List<Out> out = new ArrayList<>();
        Map<String, Form25> latest = new LinkedHashMap<>();
        Map<String, String> kindOf = new HashMap<>();
        for (Form25 f : forms) {
            Curated cur = curation.get(f.accession());
            if (cur != null && cur.exclude()) {
                curatedUsed.add(f.accession());
                out.add(out(f, "curadoria"));
                continue;
            }
            if (f.exchange() == null) {
                out.add(out(f, "bolsa fora da D1" + (f.exchangeName() != null ? ": " + f.exchangeName() : "")));
                continue;
            }
            String kind = kind(f);
            if (kind == null && cur != null && cur.type() != null) {
                kind = AssetTypeNames.ETF_INTERNACIONAL.equals(cur.type()) ? "FUND" : "COMMON";
            }
            if (kind == null) {
                out.add(out(f, "classe fora do capital"));
                continue;
            }
            if (liveCiks.contains(f.cik()) && !"FUND".equals(kind)) {
                out.add(out(f, "empresa ainda listada (troca de bolsa ou de classe)"));
                continue;
            }
            for (Form25 part : parts(f, kind)) {
                String key = part.cik() + "|" + kind + ("FUND".equals(kind) ? "|" + part.securityClass() : "");
                Form25 prev = latest.get(key);
                if (prev != null) {
                    Form25 older = prev.effective().isBefore(part.effective()) ? prev : part;
                    out.add(out(older, "saída anterior da mesma empresa (troca de bolsa)"));
                    if (older == part) {
                        continue;
                    }
                }
                latest.put(key, part);
                kindOf.put(part.accession(), kind);
            }
        }

        // 4. Ticker, tipo e categoria
        Map<Integer, Map<String, NareitClient.Constituent>> nareitByYear = new HashMap<>();
        Map<String, String> fundTickers = fundTickers(today.getYear());
        Map<String, String[]> bdrs = bdrsByCompany();
        Map<String, Map<String, String>> categories = categoriesByType();
        // o ticker pela SEC (vários documentos por empresa), em paralelo e guardado
        Map<String, Optional<DelistedTickers.Found>> secTickers = new HashMap<>();
        List<Form25> needTicker = latest.values().stream()
                .filter(f -> !"FUND".equals(kindOf.get(f.accession())) || f.securityClass() == null
                        || !FUND.matcher(f.securityClass()).find())
                .toList();
        List<Map.Entry<String, Optional<DelistedTickers.Found>>> resolved = parallel(needTicker,
                f -> Map.entry(f.accession(), cachedTicker(f)), checkStop, "tickers procurados na SEC");
        resolved.forEach(e -> secTickers.put(e.getKey(), e.getValue()));
        List<Row> rows = new ArrayList<>();
        n = 0;
        for (Form25 f : latest.values()) {
            String kind = kindOf.get(f.accession());
            Curated cur = curation.get(f.accession());
            String ticker = null;
            String tickerSource = null;
            String tickerDoc = null;
            List<String> notes = new ArrayList<>();
            Map<String, NareitClient.Constituent> nareitYear = nareit(nareitByYear, f.effective().getYear());
            NareitClient.Constituent reit = nareitYear.get(ForeignRules.companyKey(f.company()));
            if ("FUND".equals(kind)) {
                Matcher m = f.securityClass() == null ? null : PARENTHESES_TICKER.matcher(f.securityClass());
                if (m != null && m.find()) {
                    ticker = m.group(1);
                    tickerSource = "FILING_TEXT";
                    tickerDoc = "Form 25";
                } else if (f.securityClass() != null
                        && fundTickers.containsKey(ForeignRules.companyKey(f.securityClass()))) {
                    ticker = fundTickers.get(ForeignRules.companyKey(f.securityClass()));
                    tickerSource = "FUND_SERIES";
                    tickerDoc = "Series & Class";
                }
            }
            if (ticker == null) {
                Optional<DelistedTickers.Found> found = secTickers.getOrDefault(f.accession(), Optional.empty());
                if (found.isPresent()) {
                    ticker = found.get().ticker();
                    tickerSource = found.get().source();
                    tickerDoc = found.get().document();
                }
            }
            if (ticker == null && reit != null && reit.ticker() != null) {
                ticker = reit.ticker().toUpperCase(Locale.ROOT);
                tickerSource = "NAREIT";
                tickerDoc = "Nareit " + reit.year();
            }
            if (cur != null && cur.ticker() != null) {
                curatedUsed.add(f.accession());
                ticker = cur.ticker();
                tickerSource = CURATION;
            }
            String type;
            String typeSource = "SEC_NAREIT";
            if ("FUND".equals(kind)) {
                type = AssetTypeNames.ETF_INTERNACIONAL;
            } else if ("6798".equals(f.sic()) && (reit != null || inNareit(nareitYear, ticker))) {
                type = AssetTypeNames.REIT;
            } else {
                type = AssetTypeNames.STOCK;
            }
            if (cur != null && cur.type() != null) {
                curatedUsed.add(f.accession());
                type = cur.type();
                typeSource = CURATION;
            }
            String category = null;
            String categorySource = null;
            String sicCategory = sic.category(f.sic());
            switch (type) {
                case AssetTypeNames.STOCK -> {
                    String[] bdr = bdrs.get(ForeignRules.companyKey(f.company()));
                    if (bdr != null && categories.get(type).containsKey(bdr[0])) {
                        category = bdr[0];
                        categorySource = "BDR";
                    } else if (sicCategory != null) {
                        category = sicCategory;
                        categorySource = "SIC";
                    }
                }
                case AssetTypeNames.REIT -> {
                    if (reit != null) {
                        category = ForeignRules.reitCategory(reit.propertySector(), reit.subsector());
                        categorySource = category == null ? null : "NAREIT";
                    }
                }
                default -> {
                    category = ForeignRules.etfCategory(f.securityClass() + " " + f.company());
                    categorySource = "ETF_RULE";
                }
            }
            if (cur != null && cur.category() != null) {
                if (categories.get(type).containsKey(cur.category())) {
                    curatedUsed.add(f.accession());
                    category = cur.category();
                    categorySource = CURATION;
                } else {
                    notes.add("curadoria: categoria " + cur.category() + " não existe em " + type);
                }
            }
            String action = ticker == null ? "SEM_TICKER" : category == null ? "SEM_CATEGORIA" : "NOVO";
            rows.add(new Row(f.accession(), f.formType(), f.cik(), f.company(), f.filed().toString(),
                    f.effective().toString(), f.exchange().name(), f.securityClass(), kind, ticker, tickerSource,
                    tickerDoc, type, typeSource, category, categorySource, f.sic(), sic.description(f.sic()),
                    reit == null ? null : reit.propertySector() + " (" + reit.year() + ")", action, null,
                    notes.isEmpty() ? null : String.join("; ", notes)));
            if (++n % 200 == 0) {
                checkStop.run();
                log.info("Deslistados: {} de {} papéis de capital resolvidos", n, latest.size());
            }
        }

        // 5. Já cadastrados (rodada anterior ou curadoria do administrador)
        List<Row> marked = new ArrayList<>();
        int existing = 0;
        for (Row r : rows) {
            Long id = r.ticker() == null ? null : existingId(r);
            if (id != null) {
                existing++;
                marked.add(withAction(r, "JA_CADASTRADO", id));
            } else {
                marked.add(r);
            }
        }
        rows = marked;

        // 6. Gravação (WRITE): inativos, com a data de saída e a prova
        int created = 0;
        if (write) {
            created = insert(rows);
        } else {
            created = (int) rows.stream().filter(r -> "NOVO".equals(r.action())).count();
        }
        int capital = rows.size();
        int withTicker = (int) rows.stream().filter(r -> r.ticker() != null).count();
        int withoutCategory = (int) rows.stream().filter(r -> "SEM_CATEGORIA".equals(r.action())).count();
        Path preview = writePreview(from, to, write, forms.size(), rows, out, curation.size(), curatedUsed.size());
        Result result = new Result(from, to, forms.size(), capital, withTicker, created, existing, withoutCategory,
                curatedUsed.size(), write, preview);
        log.info("Deslistados do exterior ({}): {}", reason, result);
        return result;
    }

    /** Pedidos à SEC em paralelo (o ritmo de 8 por segundo continua no cliente); nulos ficam de fora. */
    private <T, R> List<R> parallel(List<T> items, java.util.function.Function<T, R> fn, Runnable checkStop,
                                    String label) {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(THREADS);
        try {
            List<java.util.concurrent.Future<R>> futures = new ArrayList<>();
            for (T item : items) {
                futures.add(pool.submit(() -> fn.apply(item)));
            }
            List<R> out = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    R r = futures.get(i).get();
                    if (r != null) {
                        out.add(r);
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    log.warn("Deslistados: falha em um item ({}): {}", label, e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Deslistados interrompido", e);
                }
                if ((i + 1) % 250 == 0) {
                    checkStop.run();
                    log.info("Deslistados: {} de {} {}", i + 1, futures.size(), label);
                }
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Um Form 25 de fundo pode tirar várias séries de uma vez ("iShares A ETF; iShares B ETF"): uma linha por série,
     * com o número de acesso seguido de {@code #n} (a chave da curadoria).
     */
    static List<Form25> parts(Form25 f, String kind) {
        if (!"FUND".equals(kind) || f.securityClass() == null || !f.securityClass().contains(";")) {
            return List.of(f);
        }
        List<Form25> out = new ArrayList<>();
        int i = 0;
        for (String p : f.securityClass().split(";")) {
            String cls = p.trim();
            if (cls.isEmpty()) {
                continue;
            }
            out.add(new Form25(f.accession() + "#" + (++i), f.formType(), f.cik(), f.company(), f.filed(),
                    f.effective(), f.sic(), f.exchangeName(), f.exchange(), cls));
        }
        return out.size() <= 1 ? List.of(f) : out;
    }

    private static boolean inNareit(Map<String, NareitClient.Constituent> year, String ticker) {
        return ticker != null && year.values().stream().anyMatch(c -> ticker.equalsIgnoreCase(c.ticker()));
    }

    /** Espécie da classe: FUND (cota de ETF ou fundo fechado), COMMON (ação, ADS, unit de MLP) ou nula (fora). */
    static String kind(Form25 f) {
        String c = f.securityClass();
        if (c == null) {
            return null;
        }
        boolean fundName = FUND.matcher(c).find() || (FUND.matcher(f.company()).find()
                && c.toLowerCase(Locale.ROOT).contains("beneficial interest"));
        if (fundName && !c.toLowerCase(Locale.ROOT).contains("notes")) {
            return "FUND";
        }
        if (COMMON.matcher(c).find()) {
            return "COMMON";
        }
        if (NOT_EQUITY_ONLY.matcher(c).find()) {
            return null;
        }
        return null;
    }

    // ------------------------------------------------------------------ ticker com cache

    /** O ticker pela SEC, guardado por número de acesso (o não achado é tentado de novo depois de 180 dias). */
    private Optional<DelistedTickers.Found> cachedTicker(Form25 f) {
        Path file = cacheDir.resolve(f.accession() + ".json");
        if (Files.isRegularFile(file)) {
            try {
                JsonNode n = mapper.readTree(file.toFile());
                LocalDate at = LocalDate.parse(n.path("at").asText());
                if (n.hasNonNull("ticker")) {
                    return Optional.of(new DelistedTickers.Found(n.get("ticker").asText(), n.path("source").asText(),
                            n.path("document").asText(null)));
                }
                if (at.isAfter(LocalDate.now().minusDays(RETRY_DAYS))) {
                    return Optional.empty();
                }
            } catch (IOException | RuntimeException e) {
                log.debug("Deslistados: cache ilegivel {}", file);
            }
        }
        Optional<DelistedTickers.Found> found = tickers.find(f.cik(), f.filed());
        try {
            Files.createDirectories(cacheDir);
            ObjectNode n = mapper.createObjectNode();
            n.put("cik", f.cik());
            n.put("at", LocalDate.now().toString());
            n.put("ticker", found.map(DelistedTickers.Found::ticker).orElse(null));
            n.put("source", found.map(DelistedTickers.Found::source).orElse(null));
            n.put("document", found.map(DelistedTickers.Found::document).orElse(null));
            mapper.writeValue(file.toFile(), n);
        } catch (IOException e) {
            log.warn("Deslistados: nao gravou o cache {}: {}", file, e.getMessage());
        }
        return found;
    }

    // ------------------------------------------------------------------ gravação

    private Long existingId(Row r) {
        List<Long> ids = jdbc.queryForList("""
                SELECT a.id FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                  LEFT JOIN foreign_listings f ON f.asset_id = a.id
                 WHERE a.delisted_on = ? AND ((upper(a.ticker) = ? AND t.name = ?) OR f.cik = ?)
                 LIMIT 1""", Long.class, java.sql.Date.valueOf(r.delistedOn()), r.ticker().toUpperCase(Locale.ROOT),
                r.type(), r.cik());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private int insert(List<Row> rows) {
        Map<String, Long> typeIds = new HashMap<>();
        jdbc.query("SELECT name, id FROM asset_types", rs -> {
            typeIds.put(rs.getString(1), rs.getLong(2));
        });
        Map<String, Long> categoryIds = new HashMap<>();
        jdbc.query("""
                SELECT t.name, c.name, c.id FROM asset_categories c JOIN asset_types t ON t.id = c.asset_type_id
                 WHERE t.name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL')""",
                rs -> {
                    categoryIds.put(rs.getString(1) + "|" + rs.getString(2), rs.getLong(3));
                });
        int[] created = {0};
        tx.executeWithoutResult(status -> {
            Set<String> taken = new HashSet<>();
            for (Row r : rows) {
                if (!"NOVO".equals(r.action())) {
                    continue;
                }
                String key = r.ticker().toUpperCase(Locale.ROOT) + "|" + r.type() + "|" + r.delistedOn();
                if (!taken.add(key)) {
                    continue;
                }
                Long id = jdbc.queryForObject("""
                        INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, currency_code, country_code,
                                            active, delisted_on, delisting_source)
                        VALUES (?, ?, ?, ?, 'USD', 'US', false, ?, 'FORM_25') RETURNING id""", Long.class,
                        r.ticker(), name(r), typeIds.get(r.type()), categoryIds.get(r.type() + "|" + r.category()),
                        java.sql.Date.valueOf(r.delistedOn()));
                jdbc.update("""
                        INSERT INTO foreign_listings (asset_id, exchange, cik, sic_code, type_source, category_source,
                                                      ticker_source)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                        id, r.exchange(), r.cik(), r.sic() != null && r.sic().matches("\\d{3,4}") ? r.sic() : null,
                        r.typeSource(), r.categorySource(), r.tickerSource());
                created[0]++;
            }
        });
        return created[0];
    }

    /** Nome do papel: o fundo pela classe (a série), a empresa pelo nome da SEC. */
    private static String name(Row r) {
        String n = "FUND".equals(r.kind()) && r.securityClass() != null
                ? r.securityClass().replaceAll("\\s*\\([A-Z.]{1,6}\\)\\s*$", "") : r.company();
        return n.length() > 255 ? n.substring(0, 255) : n;
    }

    private static Row withAction(Row r, String action, Long id) {
        return new Row(r.accession(), r.formType(), r.cik(), r.company(), r.filed(), r.delistedOn(), r.exchange(),
                r.securityClass(), r.kind(), r.ticker(), r.tickerSource(), r.tickerDocument(), r.type(), r.typeSource(),
                r.category(), r.categorySource(), r.sic(), r.sicDescription(), r.nareitSector(), action, id, r.note());
    }

    private static Out out(Form25 f, String reason) {
        return new Out(f.accession(), f.cik(), f.company(), f.filed().toString(), f.exchangeName(), f.securityClass(),
                reason);
    }

    // ------------------------------------------------------------------ fontes

    private Map<String, NareitClient.Constituent> nareit(Map<Integer, Map<String, NareitClient.Constituent>> cache,
                                                         int year) {
        return cache.computeIfAbsent(year, y -> {
            Map<String, NareitClient.Constituent> m = new HashMap<>();
            for (int yy : new int[]{y, y - 1}) {
                try {
                    nareit.constituents(yy, false).ifPresent(p -> NareitClient.parse(p, yy).forEach(c -> {
                        m.putIfAbsent(ForeignRules.companyKey(c.name()), c);
                    }));
                } catch (RuntimeException e) {
                    log.warn("Deslistados: Nareit {} ilegivel: {}", yy, e.getMessage());
                }
            }
            return m;
        });
    }

    /** Fundos das séries da SEC (os arquivos publicados, 2023 em diante): nome da série/classe → ticker. */
    private Map<String, String> fundTickers(int year) {
        Map<String, String> out = new HashMap<>();
        for (int y = year; y >= 2023; y--) {
            try {
                sec.seriesClass(y, java.time.Duration.ofDays(30)).ifPresent(p -> SecClient.parseSeriesClass(p)
                        .forEach(fc -> {
                            if (fc.seriesName() != null) {
                                out.putIfAbsent(ForeignRules.companyKey(fc.seriesName()), fc.ticker());
                            }
                            if (fc.className() != null) {
                                out.putIfAbsent(ForeignRules.companyKey(fc.className()), fc.ticker());
                            }
                        }));
            } catch (RuntimeException e) {
                log.warn("Deslistados: séries da SEC {} indisponíveis: {}", y, e.getMessage());
            }
        }
        return out;
    }

    private Map<String, String[]> bdrsByCompany() {
        Map<String, String[]> out = new HashMap<>();
        jdbc.query("""
                SELECT a.name, c.name, a.ticker FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                  JOIN asset_categories c ON c.id = a.asset_category_id
                 WHERE t.name = 'BDR' AND c.name <> 'ETFS_NO_EXTERIOR'""",
                rs -> {
                    out.putIfAbsent(ForeignRules.companyKey(rs.getString(1)),
                            new String[]{rs.getString(2), rs.getString(3)});
                });
        return out;
    }

    private Map<String, Map<String, String>> categoriesByType() {
        Map<String, Map<String, String>> out = new HashMap<>();
        for (String t : List.of(AssetTypeNames.STOCK, AssetTypeNames.REIT, AssetTypeNames.ETF_INTERNACIONAL)) {
            out.put(t, new TreeMap<>());
        }
        jdbc.query("""
                SELECT t.name, c.name, c.description FROM asset_categories c JOIN asset_types t ON t.id = c.asset_type_id
                 WHERE t.name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL')""",
                rs -> {
                    out.get(rs.getString(1)).put(rs.getString(2), rs.getString(3));
                });
        return out;
    }

    private Map<String, Curated> loadCuration() {
        Path file = dir.resolve("curadoria-deslistados.json");
        Map<String, Curated> out = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            for (JsonNode n : mapper.readTree(file.toFile()).path("itens")) {
                String acc = n.path("acesso").asText(null);
                if (acc != null) {
                    out.put(acc, new Curated(upper(n, "ticker"), upper(n, "tipo"), upper(n, "categoria"),
                            n.path("excluir").asBoolean(false)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Curadoria dos deslistados ilegível (" + file + "): " + e.getMessage(), e);
        }
        return out;
    }

    private static String upper(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim().toUpperCase(Locale.ROOT);
    }

    private Path writePreview(int from, int to, boolean write, int forms, List<Row> rows, List<Out> out,
                              int curationItems, int curated) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("geradoEm", LocalDateTime.now().withNano(0).toString());
        root.put("de", from);
        root.put("ate", to);
        root.put("gravou", write);
        root.put("form25", forms);
        // cobertura por ano e tipo: papéis de capital e quantos têm ticker
        Map<String, Map<String, int[]>> coverage = new TreeMap<>();
        for (Row r : rows) {
            int[] c = coverage.computeIfAbsent(r.delistedOn().substring(0, 4), k -> new TreeMap<>())
                    .computeIfAbsent(r.type(), k -> new int[2]);
            c[0]++;
            if (r.ticker() != null) {
                c[1]++;
            }
        }
        List<Map<String, Object>> cov = new ArrayList<>();
        coverage.forEach((y, m) -> m.forEach((t, c) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("ano", y);
            e.put("tipo", t);
            e.put("papeis", c[0]);
            e.put("comTicker", c[1]);
            cov.add(e);
        }));
        root.put("cobertura", cov);
        Map<String, Long> reasons = new TreeMap<>();
        out.forEach(o -> reasons.merge(o.reason().replaceAll(":.*", ""), 1L, Long::sum));
        root.put("foraPorMotivo", reasons);
        rows.sort(Comparator.comparing(Row::delistedOn).reversed());
        root.put("deslistados", rows);
        root.put("fora", out);
        Map<String, Object> cur = new LinkedHashMap<>();
        cur.put("itens", curationItems);
        cur.put("aplicados", curated);
        root.put("curadoria", cur);
        Path file = dir.resolve("deslistados-previa.json");
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("deslistados-previa.json.tmp");
            mapper.writeValue(tmp.toFile(), root);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Não gravou a prévia dos deslistados em " + file, e);
        }
        return file;
    }
}
