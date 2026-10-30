package com.activest.adm.domain.foreign;

import com.activest.adm.domain.asset.CatalogDelisting;
import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.foreign.NareitClient;
import com.activest.adm.integration.foreign.NasdaqTraderClient;
import com.activest.adm.integration.foreign.NasdaqTraderClient.Listing;
import com.activest.adm.integration.foreign.OpenFigiClient;
import com.activest.adm.integration.foreign.OpenFigiClient.Figi;
import com.activest.adm.integration.foreign.SecClient;
import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetTypeNames;
import com.activest.domain.asset.DelistingSource;
import com.activest.domain.asset.PriceSource;
import com.activest.domain.notification.NotificationKind;
import com.activest.domain.notification.NotificationService;
import com.activest.domain.portfolio.OpenPositions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Catálogo vivo dos EUA — a tarefa {@code FOREIGN_CATALOG} (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C1–C5, C7 dos
 * EUA e C9; etapa F4). Junta as fontes e decide, papel a papel:
 *
 * <ol>
 *   <li><b>Universo</b> (C1): a fotografia do dia da lista do Nasdaq Trader, nas bolsas da D1, sem test issue; warrant,
 *       right, unit de SPAC, nota e papel estruturado ficam fora pelo nome;</li>
 *   <li><b>Tipo</b> (C2): marca de ETF da bolsa, preferencial pelo símbolo, ou o {@code securityType} do OpenFIGI;
 *       Nareit e SIC 6798 só conferem (aba "Divergências REIT");</li>
 *   <li><b>Categoria</b> (C4): STOCK pelo BDR da mesma empresa, depois pela tabela SIC; REIT pelo setor da Nareit; ETF
 *       pelas palavras do nome;</li>
 *   <li><b>Identidade</b> (C5): FIGI composto, depois a série da SEC (ETF), depois (ticker, tipo). Mesmo FIGI com
 *       ticker novo = renomeação do mesmo ativo;</li>
 *   <li><b>Saída</b> (C7): o ativo que sumiu da lista sai com Form 25 da empresa (data = arquivamento + 10 dias) ou
 *       depois de {@value #GONE_DAYS} dias sumido (data = último dia visto); a decisão do administrador
 *       ({@code MANUAL}) nunca é refeita;</li>
 *   <li><b>Curadoria</b> (C9): {@code levantamento/internacional/curadoria.json}, com a origem {@code CURATION}, que a
 *       tarefa nunca sobrescreve.</li>
 * </ol>
 *
 * <p>Toda rodada grava a prévia {@code levantamento/internacional/catalogo-previa.json} (a planilha da D12 sai dela pelo
 * script {@code carga/scripts/levantamento/internacional_planilha.py}); só com {@code WRITE} o catálogo é alterado.
 * Papel sem categoria não é gravado: espera a curadoria.</p>
 */
@Slf4j
@Service
public class ForeignCatalogService {

    /** Dias sumido da lista para a saída sem Form 25 (C7). */
    static final int GONE_DAYS = 30;
    /** Form 25 que vale como prova: arquivado a partir de tantos dias antes do último dia visto. */
    static final int FORM25_WINDOW_DAYS = 45;
    /** Efetivação do Form 25: 10 dias depois do arquivamento (regra da SEC 12d2-2). */
    static final int FORM25_EFFECTIVE_DAYS = 10;
    static final Duration FIGI_MAX_AGE_WITH_KEY = Duration.ofDays(6);
    static final Duration FIGI_MAX_AGE_WITHOUT_KEY = Duration.ofDays(90);
    static final Duration PROFILE_MAX_AGE = Duration.ofDays(90);
    static final Duration SEC_LIST_MAX_AGE = Duration.ofDays(6);
    static final Duration SERIES_MAX_AGE = Duration.ofDays(30);
    static final String CURATION = "CURATION";
    /** Prefixo da chave do aviso de renomeação do exterior (a conferência da B3 não o fecha). */
    public static final String RENAME_KEY_PREFIX = "EUA:";
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> TYPES = List.of(AssetTypeNames.STOCK, AssetTypeNames.REIT,
            AssetTypeNames.ETF_INTERNACIONAL);

    private final NasdaqTraderClient nasdaq;
    private final OpenFigiClient figi;
    private final SecClient sec;
    private final NareitClient nareit;
    private final SicCategories sic;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AdminNotices notices;
    private final OpenPositions positions;
    private final Path dir;
    private final Path logos;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    public ForeignCatalogService(NasdaqTraderClient nasdaq, OpenFigiClient figi, SecClient sec, NareitClient nareit,
                                 SicCategories sic, JdbcTemplate jdbc, PlatformTransactionManager txManager,
                                 AdminNotices notices, OpenPositions positions,
                                 @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this.nasdaq = nasdaq;
        this.figi = figi;
        this.sec = sec;
        this.nareit = nareit;
        this.sic = sic;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.notices = notices;
        this.positions = positions;
        this.dir = Path.of(dataDir, "levantamento", "internacional").toAbsolutePath().normalize();
        this.logos = Path.of(dataDir, "logos").toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------ resultado e prévia

    /** O que a rodada achou e (com {@code WRITE}) gravou. */
    public record Result(LocalDate snapshot, int listed, int stocks, int reits, int etfs, int excluded,
                         int withoutCategory, int created, int updated, int renamed, int inactivated, int waitingGone,
                         int curated, int curationProblems, boolean wrote, Path preview) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "lista de %s: %d papéis; catálogo: %d Stock, %d REIT, %d ETF; %d fora; "
                            + "%d sem categoria; %s; curadoria: %d aplicados, %d problemas; prévia: %s",
                    snapshot, listed, stocks, reits, etfs, excluded, withoutCategory,
                    wrote ? String.format(Locale.ROOT, "gravados %d novos, %d atualizados, %d renomeados, %d inativados "
                            + "(%d aguardando os %d dias)", created, updated, renamed, inactivated, waitingGone, GONE_DAYS)
                            : String.format(Locale.ROOT, "sem gravar (WRITE=false): %d novos, %d atualizados, "
                            + "%d renomeados, %d a inativar", created, updated, renamed, inactivated),
                    curated, curationProblems, preview.getFileName());
        }
    }

    /** Um papel do catálogo vivo (aba Stocks, REITs ou ETFs). */
    public record Row(String ticker, String symbol, String name, String type, String typeSource, String category,
                      String categorySource, String bdrCategory, String bdrTicker, String sicCategory, String sic,
                      String sicDescription, String nareitSector, String exchange, String cik, String seriesId,
                      String compositeFigi, String figiSecurityType, String tickerSource, String action, Long assetId,
                      String previousTicker, String note) {
    }

    /** Papel fora (aba Excluídos). */
    public record Excluded(String ticker, String symbol, String name, String exchange, String reason,
                           String figiSecurityType) {
    }

    /** Ativo do catálogo que sumiu da lista (aba Deslistados). */
    public record Gone(Long assetId, String ticker, String type, String name, String status, String source,
                       String delistedOn, String lastSeen, String proof) {
    }

    public record Collision(String ticker, String type, String otherType, String otherName) {
    }

    public record ReitDivergence(String ticker, String name, String type, String figiSecurityType, String nareitSector,
                                 String sic, String note) {
    }

    public record CategoryInfo(String type, String category, String description) {
    }

    // ------------------------------------------------------------------ o que vem do banco

    private record Existing(Long id, String ticker, String name, String type, String category, boolean active,
                            String delistingSource, boolean hasListing, String exchange, String cik, String seriesId,
                            String figi, String typeSource, String categorySource, String tickerSource,
                            LocalDate lastSeen) {
    }

    /** Uma linha em montagem (antes da identidade). */
    private static final class Draft {
        Listing listing;
        String ticker;
        String tickerSource = "LIST";
        String name;
        String type;
        String typeSource;
        String category;
        String categorySource;
        String bdrCategory;
        String bdrTicker;
        String sicCategory;
        String sic;
        String sicDescription;
        String nareitSector;
        String cik;
        String seriesId;
        Figi figi;
        final List<String> notes = new ArrayList<>();
    }

    // ------------------------------------------------------------------ a rodada

    /**
     * @param write     grava o catálogo (o portão da D12); falso = só a prévia
     * @param checkStop chamado entre as etapas longas (Stop da tarefa)
     */
    public synchronized Result run(boolean write, String reason, Runnable checkStop) {
        LocalDate today = NotificationService.today();
        log.info("Catalogo do exterior ({}): inicio, WRITE={}", reason, write);

        // 1. Universo (C1)
        List<Listing> listings = NasdaqTraderClient.parse(nasdaq.snapshot(today)).stream()
                .filter(l -> l.exchange() != null && !l.testIssue())
                .toList();
        ForeignCuration curation = loadCuration();
        List<String> curationProblems = new ArrayList<>();
        Set<String> curationUsed = new HashSet<>();

        // 2. OpenFIGI (tipo e identidade)
        List<String> ask = listings.stream()
                .filter(l -> ForeignRules.excludedByName(l) == null)
                .map(l -> ForeignRules.bloomberg(l.symbol()))
                .filter(Objects::nonNull).distinct().toList();
        Map<String, Figi> figis = figi.mapTickers(ask, figi.hasKey() ? FIGI_MAX_AGE_WITH_KEY : FIGI_MAX_AGE_WITHOUT_KEY);
        checkStop.run();

        // 3. Tipo (C2) e exclusões, com a curadoria de tipo e de exclusão
        List<Draft> drafts = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (Listing l : listings) {
            String ticker = ForeignRules.ticker(l.symbol());
            String bb = ForeignRules.bloomberg(l.symbol());
            Figi f = bb == null ? null : figis.get(bb);
            ForeignCuration.Item cur = curation.get(ticker);
            String why = ForeignRules.excludedByName(l);
            ForeignRules.Typed typed = why == null ? ForeignRules.type(l, f) : null;
            if (why == null && typed == null) {
                why = ForeignRules.whyNot(f);
            }
            if (cur != null && cur.exclude()) {
                curationUsed.add(ticker);
                excluded.add(new Excluded(ticker, l.symbol(), l.name(), l.exchange().name(), "curadoria",
                        f == null ? null : f.securityType()));
                continue;
            }
            if (cur != null && cur.type() != null) {
                if (!TYPES.contains(cur.type())) {
                    curationProblems.add(ticker + ": tipo desconhecido " + cur.type());
                } else {
                    curationUsed.add(ticker);
                    typed = new ForeignRules.Typed(cur.type(), CURATION);
                    why = null;
                }
            }
            if (typed == null) {
                excluded.add(new Excluded(ticker, l.symbol(), l.name(), l.exchange().name(), why,
                        f == null ? null : f.securityType()));
                continue;
            }
            Draft d = new Draft();
            d.listing = l;
            d.ticker = ticker;
            d.name = ForeignRules.displayName(l);
            d.type = typed.type();
            d.typeSource = typed.source();
            d.figi = f;
            if (cur != null && cur.newTicker() != null) {
                curationUsed.add(ticker);
                d.ticker = cur.newTicker();
                d.tickerSource = CURATION;
            }
            drafts.add(d);
        }

        // 4. Fontes da categoria e da identidade: SEC (CIK, SIC, séries dos fundos), Nareit, BDRs
        Map<String, SecClient.Ticker> secTickers = new HashMap<>();
        for (SecClient.Ticker t : sec.parseCompanyTickers(sec.companyTickers(SEC_LIST_MAX_AGE))) {
            if (t.ticker() != null && t.cik() != null) {
                secTickers.putIfAbsent(t.ticker().toUpperCase(Locale.ROOT), t);
            }
        }
        Map<String, SecClient.FundClass> funds = fundsByTicker(today.getYear());
        Map<String, NareitClient.Constituent> reits = nareitByTicker(today.getYear());
        Map<String, String[]> bdrs = bdrsByCompany();
        checkStop.run();
        for (Draft d : drafts) {
            String sym = d.listing.symbol();
            SecClient.Ticker st = secTickers.get(ForeignRules.secTicker(sym));
            if (st == null) {
                st = secTickers.get(ForeignRules.root(sym));
            }
            if (AssetTypeNames.ETF_INTERNACIONAL.equals(d.type)) {
                SecClient.FundClass fc = funds.get(ForeignRules.ticker(sym));
                if (fc != null) {
                    d.seriesId = fc.seriesId();
                    d.cik = fc.cik() == null ? null : String.valueOf(Long.parseLong(fc.cik()));
                } else if (st != null) {
                    d.cik = String.valueOf(Long.parseLong(st.cik()));
                }
            } else if (st != null) {
                d.cik = String.valueOf(Long.parseLong(st.cik()));
            }
            NareitClient.Constituent nc = reits.get(ForeignRules.ticker(sym));
            if (nc != null) {
                d.nareitSector = nc.propertySector() + (nc.subsector() != null ? " / " + nc.subsector() : "")
                        + " (" + nc.year() + ")";
            }
            String[] bdr = bdrs.get(ForeignRules.companyKey(d.name));
            if (bdr == null && st != null) {
                bdr = bdrs.get(ForeignRules.companyKey(st.name()));
            }
            if (bdr != null) {
                d.bdrCategory = bdr[0];
                d.bdrTicker = bdr[1];
            }
        }

        // 5. SIC das empresas (ficha da SEC; a primeira carga baixa uma por CIK, 8 por segundo)
        Map<String, SecClient.Profile> profiles = new HashMap<>();
        int n = 0;
        for (String cik : drafts.stream().filter(d -> !AssetTypeNames.ETF_INTERNACIONAL.equals(d.type))
                .map(d -> d.cik).filter(Objects::nonNull).distinct().toList()) {
            sec.profile(cik, PROFILE_MAX_AGE).ifPresent(p -> profiles.put(cik, p));
            if (++n % 200 == 0) {
                checkStop.run();
                log.info("Catalogo do exterior: {} fichas da SEC lidas", n);
            }
        }

        // 6. Categoria (C4) e conferência do REIT (D5)
        Map<String, Map<String, String>> categories = categoriesByType();
        List<ReitDivergence> divergences = new ArrayList<>();
        for (Draft d : drafts) {
            SecClient.Profile p = d.cik == null ? null : profiles.get(d.cik);
            if (p != null && p.sic() != null) {
                d.sic = p.sic();
                d.sicDescription = p.sicDescription() != null ? p.sicDescription() : sic.description(p.sic());
                d.sicCategory = sic.category(p.sic());
            }
            switch (d.type) {
                case AssetTypeNames.STOCK -> {
                    if (d.bdrCategory != null && categories.get(d.type).containsKey(d.bdrCategory)) {
                        d.category = d.bdrCategory;
                        d.categorySource = "BDR";
                    } else if (d.sicCategory != null) {
                        d.category = d.sicCategory;
                        d.categorySource = "SIC";
                    } else if (p != null && p.sic() == null) {
                        // a SEC não dá SIC a empresa de investimento (BDC, fundo fechado e as preferenciais deles)
                        d.category = "FINANCAS";
                        d.categorySource = "SIC";
                        d.notes.add("sem SIC na SEC (empresa de investimento): Finanças");
                    }
                }
                case AssetTypeNames.REIT -> {
                    NareitClient.Constituent nc = reits.get(ForeignRules.ticker(d.listing.symbol()));
                    String c = nc == null ? null : ForeignRules.reitCategory(nc.propertySector(), nc.subsector());
                    if (c != null) {
                        d.category = c;
                        d.categorySource = "NAREIT";
                    }
                }
                default -> {
                    d.category = ForeignRules.etfCategory(d.name);
                    d.categorySource = "ETF_RULE";
                }
            }
            ForeignCuration.Item cur = curation.get(ForeignRules.ticker(d.listing.symbol()));
            if (cur != null && cur.category() != null) {
                if (categories.get(d.type).containsKey(cur.category())) {
                    curationUsed.add(cur.ticker());
                    d.category = cur.category();
                    d.categorySource = CURATION;
                } else {
                    curationProblems.add(cur.ticker() + ": categoria " + cur.category() + " não existe em " + d.type);
                }
            }
            boolean preferred = ForeignRules.preferred(d.listing);
            boolean reitBySources = d.nareitSector != null || "6798".equals(d.sic);
            boolean reitByType = AssetTypeNames.REIT.equals(d.type);
            if (!preferred && !AssetTypeNames.ETF_INTERNACIONAL.equals(d.type) && reitByType != reitBySources) {
                divergences.add(new ReitDivergence(d.ticker, d.name, d.type,
                        d.figi == null ? null : d.figi.securityType(), d.nareitSector, d.sic,
                        reitByType ? "OpenFIGI diz REIT; Nareit e SIC não" : "Nareit ou SIC 6798 dizem REIT; OpenFIGI não"));
            }
        }

        // 7. Identidade (C5) contra o catálogo
        Map<Long, Existing> existing = existing();
        Map<String, Existing> byFigi = new HashMap<>();
        Map<String, Existing> bySeries = new HashMap<>();
        Map<String, Existing> byTicker = new HashMap<>();
        for (Existing e : existing.values()) {
            if (e.figi() != null) {
                byFigi.put(e.figi(), e);
            }
            if (e.seriesId() != null) {
                bySeries.put(e.seriesId(), e);
            }
            if (e.active()) {
                byTicker.put(key(e.ticker(), e.type()), e);
            }
        }
        Set<String> figisSeen = new HashSet<>();
        Set<String> seriesSeen = new HashSet<>();
        Set<String> tickersSeen = new HashSet<>();
        Set<Long> matched = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        Map<Row, Draft> draftOf = new LinkedHashMap<>();
        for (Draft d : drafts) {
            if (!tickersSeen.add(key(d.ticker, d.type))) {
                excluded.add(new Excluded(d.ticker, d.listing.symbol(), d.listing.name(), d.listing.exchange().name(),
                        "ticker repetido na lista", d.figi == null ? null : d.figi.securityType()));
                continue;
            }
            String composite = d.figi == null ? null : d.figi.compositeFigi();
            if (composite != null && !figisSeen.add(composite)) {
                d.notes.add("FIGI " + composite + " repetido na lista: gravado sem FIGI");
                composite = null;
            }
            if (d.seriesId != null && !seriesSeen.add(d.seriesId)) {
                d.notes.add("série " + d.seriesId + " repetida na lista: gravado sem série");
                d.seriesId = null;
            }
            Existing e = composite == null ? null : byFigi.get(composite);
            if (e == null && d.seriesId != null) {
                e = bySeries.get(d.seriesId);
            }
            if (e == null) {
                e = byTicker.get(key(d.ticker, d.type));
            }
            if (e != null && !matched.add(e.id())) {
                d.notes.add("o ativo " + e.ticker() + " já foi casado com outro papel da lista");
                e = null;
            }
            String action;
            String previous = null;
            if (e == null) {
                action = d.category == null ? "SEM_CATEGORIA" : "NOVO";
            } else if (!e.active()) {
                action = "INATIVO_NO_CATALOGO";
                d.notes.add("o papel está na lista e o ativo " + e.ticker() + " está inativo (" + e.delistingSource()
                        + "): conferir");
            } else {
                if (!e.ticker().equalsIgnoreCase(d.ticker) && !CURATION.equals(e.tickerSource())) {
                    action = "RENOMEIA";
                    previous = e.ticker();
                } else {
                    action = differs(e, d) ? "ATUALIZA" : "IGUAL";
                }
                if (!e.type().equals(d.type) && CURATION.equals(d.typeSource)) {
                    d.notes.add("tipo trocado pela curadoria: " + e.type() + " → " + d.type);
                    if ("IGUAL".equals(action)) {
                        action = "ATUALIZA";
                    }
                } else if (!e.type().equals(d.type)) {
                    d.notes.add("tipo no catálogo " + e.type() + ", pela regra " + d.type + " (o tipo não é trocado "
                            + "sozinho; a curadoria troca)");
                }
                if (CURATION.equals(e.categorySource()) && !CURATION.equals(d.categorySource)) {
                    d.category = e.category();
                    d.categorySource = CURATION;
                }
            }
            String rowType = e == null || CURATION.equals(d.typeSource) ? d.type : e.type();
            Row r = new Row(d.ticker, d.listing.symbol(), d.name, rowType, d.typeSource,
                    d.category, d.categorySource, d.bdrCategory, d.bdrTicker, d.sicCategory, d.sic, d.sicDescription,
                    d.nareitSector, d.listing.exchange().name(), d.cik, d.seriesId, composite,
                    d.figi == null ? null : d.figi.securityType(), d.tickerSource, action, e == null ? null : e.id(),
                    previous, d.notes.isEmpty() ? null : String.join("; ", d.notes));
            rows.add(r);
            draftOf.put(r, d);
        }

        // 8. Saída (C7): ativos do catálogo que sumiram da lista
        Set<Long> held = new HashSet<>();
        positions.assetsWithShares(PriceSource.MANUAL).forEach(a -> held.add(a.getId()));
        List<Gone> gone = new ArrayList<>();
        for (Existing e : existing.values()) {
            if (!e.active() || matched.contains(e.id())) {
                continue;
            }
            if ("MANUAL".equals(e.delistingSource())) {
                gone.add(new Gone(e.id(), e.ticker(), e.type(), e.name(), "MANTIDO", "MANUAL", null,
                        str(e.lastSeen()), "reativado pelo administrador: a tarefa não inativa de novo"));
                continue;
            }
            gone.add(goneOf(e, today));
        }

        // 9. Colisões com os outros tipos (META ação e META cripto convivem — D3)
        List<Collision> collisions = collisions(rows);

        Set<String> unused = new HashSet<>(curation.items().keySet());
        unused.removeAll(curationUsed);
        unused.removeAll(excluded.stream().filter(x -> "curadoria".equals(x.reason())).map(Excluded::ticker).toList());
        unused.forEach(t -> curationProblems.add(t + ": não está na lista de hoje (curadoria não aplicada)"));

        // 10. Gravação (WRITE)
        int created = 0;
        int updated = 0;
        int renamed = 0;
        int inactivated = 0;
        if (write) {
            Applied a = apply(rows, draftOf, gone, existing, today, held);
            created = a.created();
            updated = a.updated();
            renamed = a.renamed();
            inactivated = a.inactivated();
        } else {
            created = (int) rows.stream().filter(r -> r.action().equals("NOVO")).count();
            updated = (int) rows.stream().filter(r -> r.action().equals("ATUALIZA")).count();
            renamed = (int) rows.stream().filter(r -> r.action().equals("RENOMEIA")).count();
            inactivated = (int) gone.stream().filter(g -> g.status().equals("INATIVAR")).count();
        }
        int waiting = (int) gone.stream().filter(g -> g.status().equals("AGUARDANDO")).count();

        Path preview = writePreview(today, write, rows, excluded, gone, collisions, divergences, curation,
                curationUsed.size(), curationProblems);
        Result result = new Result(today, listings.size(), count(rows, AssetTypeNames.STOCK),
                count(rows, AssetTypeNames.REIT), count(rows, AssetTypeNames.ETF_INTERNACIONAL), excluded.size(),
                (int) rows.stream().filter(r -> r.category() == null).count(), created, updated, renamed, inactivated,
                waiting, curationUsed.size(), curationProblems.size(), write, preview);
        log.info("Catalogo do exterior ({}): {}", reason, result);
        return result;
    }

    // ------------------------------------------------------------------ C7

    private Gone goneOf(Existing e, LocalDate today) {
        LocalDate last = e.lastSeen();
        if (last == null) {
            return new Gone(e.id(), e.ticker(), e.type(), e.name(), "SEM_DATA", null, null, null,
                    "fora da lista e sem a data em que foi visto: conferir");
        }
        if (e.cik() != null && !AssetTypeNames.ETF_INTERNACIONAL.equals(e.type())) {
            SecClient.Profile p = sec.profile(e.cik(), Duration.ZERO).orElse(null);
            if (p != null) {
                SecClient.Filing f = p.delistings().stream()
                        .filter(x -> !x.filed().isBefore(last.minusDays(FORM25_WINDOW_DAYS)))
                        .max((a, b) -> a.filed().compareTo(b.filed())).orElse(null);
                if (f != null) {
                    LocalDate out = f.filed().plusDays(FORM25_EFFECTIVE_DAYS);
                    if (out.isAfter(today)) {
                        out = last;
                    }
                    return new Gone(e.id(), e.ticker(), e.type(), e.name(), "INATIVAR", DelistingSource.FORM_25.name(),
                            str(out), str(last), "Form " + f.form() + " arquivado na SEC em " + f.filed().format(BR)
                            + " (" + f.accession() + "); fora da lista da bolsa desde " + last.plusDays(1).format(BR));
                }
            }
        }
        if (!last.isAfter(today.minusDays(GONE_DAYS))) {
            return new Gone(e.id(), e.ticker(), e.type(), e.name(), "INATIVAR", DelistingSource.LIST_GONE.name(),
                    str(last), str(last), "fora da lista da bolsa (Nasdaq Trader) desde " + last.plusDays(1).format(BR)
                    + ", há mais de " + GONE_DAYS + " dias, sem renomeação");
        }
        return new Gone(e.id(), e.ticker(), e.type(), e.name(), "AGUARDANDO", null, null, str(last),
                "fora da lista desde " + last.plusDays(1).format(BR) + "; inativa com " + GONE_DAYS
                        + " dias ou com o Form 25");
    }

    // ------------------------------------------------------------------ gravação

    private record Applied(int created, int updated, int renamed, int inactivated) {
    }

    private Applied apply(List<Row> rows, Map<Row, Draft> draftOf, List<Gone> gone, Map<Long, Existing> existing,
                          LocalDate today, Set<Long> held) {
        Map<String, Long> typeIds = new HashMap<>();
        jdbc.query("SELECT name, id FROM asset_types", rs -> {
            typeIds.put(rs.getString(1), rs.getLong(2));
        });
        Map<String, Long> categoryIds = new HashMap<>();
        jdbc.query("""
                SELECT t.name, c.name, c.id FROM asset_categories c JOIN asset_types t ON t.id = c.asset_type_id
                 WHERE t.name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL')""",
                rs -> {
                    categoryIds.put(key(rs.getString(2), rs.getString(1)), rs.getLong(3));
                });
        int[] counts = new int[4];
        List<Runnable> afterCommit = new ArrayList<>();
        GenericLogos generic = new GenericLogos(logos);
        tx.executeWithoutResult(status -> {
            // 1. Saídas primeiro: o ticker do papel que saiu fica livre para o vivo (P2)
            Set<String> taken = new HashSet<>();
            existing.values().stream().filter(Existing::active).forEach(e -> taken.add(key(e.ticker(), e.type())));
            for (Gone g : gone) {
                if (!"INATIVAR".equals(g.status())) {
                    continue;
                }
                int n = jdbc.update("""
                        UPDATE assets SET active = false, delisted_on = ?, delisting_source = ?
                         WHERE id = ? AND active AND (delisting_source IS NULL OR delisting_source <> 'MANUAL')""",
                        java.sql.Date.valueOf(g.delistedOn()), g.source(), g.assetId());
                if (n > 0) {
                    counts[3]++;
                    taken.remove(key(g.ticker(), g.type()));
                    afterCommit.add(() -> notifyGone(g, held));
                }
            }
            // 2. Renomeações, atualizações e novos
            for (Row r : rows) {
                Draft d = draftOf.get(r);
                switch (r.action()) {
                    case "RENOMEIA" -> {
                        if (taken.contains(key(r.ticker(), r.type()))) {
                            log.warn("Catalogo do exterior: {} -> {} adiado, o ticker ainda e de outro ativo vivo",
                                    r.previousTicker(), r.ticker());
                            continue;
                        }
                        jdbc.update("UPDATE assets SET ticker = ? WHERE id = ?", r.ticker(), r.assetId());
                        taken.remove(key(r.previousTicker(), r.type()));
                        taken.add(key(r.ticker(), r.type()));
                        updateExisting(r, d, existing.get(r.assetId()), today, categoryIds);
                        counts[2]++;
                        afterCommit.add(() -> notifyRename(r, existing.get(r.assetId()), held));
                    }
                    case "ATUALIZA", "IGUAL" -> {
                        Existing e = existing.get(r.assetId());
                        if (!e.type().equals(r.type())) {
                            Long cat = categoryIds.get(key(r.category(), r.type()));
                            if (cat == null || taken.contains(key(r.ticker(), r.type()))) {
                                log.warn("Catalogo do exterior: {} sem troca de tipo {} -> {} (categoria {} ou ticker "
                                        + "ocupado)", r.ticker(), e.type(), r.type(), r.category());
                                continue;
                            }
                            jdbc.update("UPDATE assets SET asset_type_id = ?, asset_category_id = ? WHERE id = ?",
                                    typeIds.get(r.type()), cat, e.id());
                            taken.remove(key(e.ticker(), e.type()));
                            taken.add(key(r.ticker(), r.type()));
                            counts[1]++;
                            updateExisting(r, d, e, today, categoryIds);
                            continue;
                        }
                        if (updateExisting(r, d, existing.get(r.assetId()), today, categoryIds)) {
                            counts[1]++;
                        }
                    }
                    case "NOVO" -> {
                        if (taken.contains(key(r.ticker(), r.type()))) {
                            log.warn("Catalogo do exterior: {} ({}) nao gravado, o ticker ainda e de outro ativo vivo",
                                    r.ticker(), r.type());
                            continue;
                        }
                        Long id = jdbc.queryForObject("""
                                INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, currency_code,
                                                    country_code, active)
                                VALUES (?, ?, ?, ?, 'USD', 'US', true) RETURNING id""", Long.class,
                                r.ticker(), r.name(), typeIds.get(r.type()), categoryIds.get(key(r.category(), r.type())));
                        insertListing(id, r, d, today);
                        afterCommit.add(() -> copyLogo(r, d, generic));
                        taken.add(key(r.ticker(), r.type()));
                        counts[0]++;
                    }
                    default -> {
                        // SEM_CATEGORIA e INATIVO_NO_CATALOGO esperam a curadoria
                    }
                }
            }
        });
        afterCommit.forEach(Runnable::run);
        return new Applied(counts[0], counts[1], counts[2], counts[3]);
    }

    /** Atualiza nome, categoria e os dados da listagem; a origem {@code CURATION} nunca é sobrescrita. */
    private boolean updateExisting(Row r, Draft d, Existing e, LocalDate today, Map<String, Long> categoryIds) {
        boolean changed = false;
        if (!Objects.equals(e.name(), r.name())) {
            jdbc.update("UPDATE assets SET name = ? WHERE id = ?", r.name(), e.id());
            changed = true;
        }
        Long cat = r.category() == null ? null : categoryIds.get(key(r.category(), r.type()));
        if (cat != null && !Objects.equals(e.category(), r.category())
                && (!CURATION.equals(e.categorySource()) || CURATION.equals(r.categorySource()))) {
            jdbc.update("UPDATE assets SET asset_category_id = ? WHERE id = ?", cat, e.id());
            changed = true;
        }
        if (!e.hasListing()) {
            insertListing(e.id(), r, d, today);
            return true;
        }
        changed |= !Objects.equals(e.exchange(), r.exchange()) || !Objects.equals(e.cik(), r.cik())
                || !Objects.equals(e.seriesId(), r.seriesId()) || !Objects.equals(e.figi(), r.compositeFigi());
        jdbc.update("""
                UPDATE foreign_listings
                   SET exchange = ?, cik = COALESCE(?, cik), sec_series_id = COALESCE(?, sec_series_id),
                       composite_figi = COALESCE(?, composite_figi), figi_security_type = COALESCE(?, figi_security_type),
                       sic_code = COALESCE(?, sic_code),
                       type_source = CASE WHEN type_source = 'CURATION' THEN type_source ELSE ? END,
                       category_source = CASE WHEN category_source = 'CURATION' AND ? <> 'CURATION' THEN category_source
                                              ELSE COALESCE(?, category_source) END,
                       ticker_source = CASE WHEN ticker_source = 'CURATION' THEN ticker_source ELSE ? END,
                       last_seen_on = ?, updated_at = NOW()
                 WHERE asset_id = ?""",
                r.exchange(), r.cik(), r.seriesId(), r.compositeFigi(), r.figiSecurityType(), sicCode(r.sic()),
                r.typeSource(), String.valueOf(r.categorySource()), r.categorySource(), r.tickerSource(),
                java.sql.Date.valueOf(today), e.id());
        return changed;
    }

    private void insertListing(Long assetId, Row r, Draft d, LocalDate today) {
        jdbc.update("""
                INSERT INTO foreign_listings (asset_id, exchange, cik, sec_series_id, composite_figi, figi_security_type,
                                              sic_code, type_source, category_source, ticker_source, first_seen_on,
                                              last_seen_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                assetId, r.exchange(), r.cik(), r.seriesId(), r.compositeFigi(), r.figiSecurityType(), sicCode(r.sic()),
                r.typeSource(), r.categorySource(), r.tickerSource(), java.sql.Date.valueOf(today),
                java.sql.Date.valueOf(today));
    }

    private static String sicCode(String sic) {
        return sic == null || !sic.matches("\\d{3,4}") ? null : sic;
    }

    private void notifyGone(Gone g, Set<Long> held) {
        Asset a = asset(g.assetId(), g.ticker());
        notices.assetPending(NotificationKind.CATALOGO_TICKER_INATIVADO, a, CatalogDelisting.key(g.assetId()),
                g.ticker() + " (" + g.type() + ") inativado — saiu da bolsa em "
                        + LocalDate.parse(g.delistedOn()).format(BR)
                        + (held.contains(g.assetId()) ? " (há posição aberta)" : ""),
                "Prova: " + g.proof() + ". O papel some da busca (só a Nova transação o traz, para lançamento até a data "
                        + "de saída). Se a inativação estiver errada, desfaça pela curadoria: PUT /api/v1/admin/assets/"
                        + g.assetId() + "/activation com {\"active\": true}.");
    }

    private void notifyRename(Row r, Existing e, Set<Long> held) {
        Asset a = asset(e.id(), r.ticker());
        notices.assetPending(NotificationKind.CATALOGO_TICKER_RENOMEADO, a,
                RENAME_KEY_PREFIX + r.previousTicker() + ">" + r.ticker(),
                r.previousTicker() + " agora é " + r.ticker() + " (" + r.type() + ")"
                        + (held.contains(e.id()) ? " (há posição aberta)" : ""),
                "O papel mudou de ticker na bolsa dos EUA (mesmo FIGI " + r.compositeFigi() + "). O catálogo já foi "
                        + "atualizado: posições e transações continuam no mesmo ativo.");
    }

    /**
     * Logo do papel novo (C10 e P4 do plano): a preferencial recebe o da própria empresa; os outros, o do BDR da mesma
     * empresa — menos a imagem genérica da BRAPI (o mesmo arquivo em 3 ou mais tickers). Arquivo que já existe vale.
     */
    void copyLogo(Row r, Draft d, GenericLogos generic) {
        try {
            Path target = logos.resolve(r.type());
            String stem = com.activest.domain.asset.AssetLogos.fileStem(r.ticker());
            if (!Files.isDirectory(target) || logoFile(target, stem) != null) {
                return;
            }
            Path source = null;
            if (ForeignRules.preferred(d.listing)) {
                String company = com.activest.domain.asset.AssetLogos.fileStem(
                        ForeignRules.ticker(ForeignRules.root(d.listing.symbol())));
                for (String t : TYPES) {
                    source = logoFile(logos.resolve(t), company);
                    if (source != null) {
                        break;
                    }
                }
            }
            if (source == null && r.bdrTicker() != null) {
                Path bdr = logoFile(logos, com.activest.domain.asset.AssetLogos.fileStem(r.bdrTicker()));
                if (bdr != null && !generic.isGeneric(bdr)) {
                    source = bdr;
                }
            }
            if (source == null) {
                return;
            }
            String name = source.getFileName().toString();
            Files.copy(source, target.resolve(stem + name.substring(name.lastIndexOf('.'))));
            log.info("Catalogo do exterior: logo de {} ({}) copiado de {}", r.ticker(), r.type(), name);
        } catch (IOException e) {
            log.warn("Catalogo do exterior: logo de {} nao copiado: {}", r.ticker(), e.getMessage());
        }
    }

    /** O arquivo {@code {stem}.{svg|png|jpg|jpeg|webp}} da pasta, ou nulo. */
    private static Path logoFile(Path dir, String stem) {
        for (String ext : List.of(".svg", ".png", ".jpg", ".jpeg", ".webp")) {
            Path p = dir.resolve(stem + ext);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /** As imagens genéricas da raiz dos logos (o mesmo conteúdo em 3 ou mais arquivos), lidas uma vez por rodada. */
    static final class GenericLogos {
        private final Path root;
        private Set<String> generic;

        GenericLogos(Path root) {
            this.root = root;
        }

        synchronized boolean isGeneric(Path file) throws IOException {
            if (generic == null) {
                Map<String, Integer> count = new HashMap<>();
                if (Files.isDirectory(root)) {
                    try (var s = Files.list(root)) {
                        for (Path p : s.filter(Files::isRegularFile).toList()) {
                            count.merge(hash(p), 1, Integer::sum);
                        }
                    }
                }
                generic = new HashSet<>();
                count.forEach((h, n) -> {
                    if (n >= 3) {
                        generic.add(h);
                    }
                });
            }
            return generic.contains(hash(file));
        }

        private static String hash(Path p) throws IOException {
            try {
                return java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** O ativo só para o aviso (id e ticker). */
    private static Asset asset(Long id, String ticker) {
        Asset a = new Asset();
        a.setId(id);
        a.setTicker(ticker);
        return a;
    }

    // ------------------------------------------------------------------ fontes

    private Map<String, SecClient.FundClass> fundsByTicker(int year) {
        Map<String, SecClient.FundClass> out = new HashMap<>();
        for (int y = year; y >= year - 1 && out.isEmpty(); y--) {
            sec.seriesClass(y, SERIES_MAX_AGE).ifPresent(p -> SecClient.parseSeriesClass(p).forEach(fc -> {
                if (fc.ticker() != null && fc.seriesId() != null) {
                    out.putIfAbsent(fc.ticker().trim().toUpperCase(Locale.ROOT), fc);
                }
            }));
        }
        return out;
    }

    /** O setor da Nareit do ano mais recente em que o REIT aparece (C4), dos 4 últimos anos. */
    private Map<String, NareitClient.Constituent> nareitByTicker(int year) {
        Map<String, NareitClient.Constituent> out = new HashMap<>();
        for (int y = year; y >= year - 3; y--) {
            int yy = y;
            try {
                nareit.constituents(y, false).ifPresent(p -> NareitClient.parse(p, yy).forEach(c -> {
                    if (c.ticker() != null) {
                        out.putIfAbsent(c.ticker().trim().toUpperCase(Locale.ROOT).replace('/', '.'), c);
                    }
                }));
            } catch (RuntimeException e) {
                log.warn("Catalogo do exterior: Nareit {} ilegivel: {}", y, e.getMessage());
            }
        }
        return out;
    }

    /** BDRs por empresa (nome normalizado) → [categoria, ticker], sem os ETFs (C4). */
    private Map<String, String[]> bdrsByCompany() {
        Map<String, String[]> out = new HashMap<>();
        jdbc.query("""
                SELECT a.name, c.name, a.ticker FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                  JOIN asset_categories c ON c.id = a.asset_category_id
                 WHERE t.name = 'BDR' AND c.name <> 'ETFS_NO_EXTERIOR'
                 ORDER BY a.active DESC, a.ticker""",
                rs -> {
                    String k = ForeignRules.companyKey(rs.getString(1));
                    if (!k.isEmpty()) {
                        out.putIfAbsent(k, new String[]{rs.getString(2), rs.getString(3)});
                    }
                });
        return out;
    }

    private Map<String, Map<String, String>> categoriesByType() {
        Map<String, Map<String, String>> out = new HashMap<>();
        TYPES.forEach(t -> out.put(t, new TreeMap<>()));
        jdbc.query("""
                SELECT t.name, c.name, c.description FROM asset_categories c
                  JOIN asset_types t ON t.id = c.asset_type_id
                 WHERE t.name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL') ORDER BY c.name""",
                rs -> {
                    out.get(rs.getString(1)).put(rs.getString(2), rs.getString(3));
                });
        return out;
    }

    private Map<Long, Existing> existing() {
        Map<Long, Existing> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.id, a.ticker, a.name, t.name, c.name, a.active, a.delisting_source, f.asset_id IS NOT NULL,
                       f.exchange, f.cik, f.sec_series_id, f.composite_figi, f.type_source, f.category_source,
                       f.ticker_source, f.last_seen_on
                  FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                  LEFT JOIN asset_categories c ON c.id = a.asset_category_id
                  LEFT JOIN foreign_listings f ON f.asset_id = a.id
                 WHERE t.name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL')
                 ORDER BY a.id""",
                rs -> {
                    java.sql.Date seen = rs.getDate(16);
                    out.put(rs.getLong(1), new Existing(rs.getLong(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getString(7), rs.getBoolean(8),
                            rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
                            rs.getString(14), rs.getString(15), seen == null ? null : seen.toLocalDate()));
                });
        return out;
    }

    private List<Collision> collisions(List<Row> rows) {
        Map<String, List<String[]>> others = new HashMap<>();
        jdbc.query("""
                SELECT upper(a.ticker), t.name, a.name FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                 WHERE t.name NOT IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL') AND a.active""",
                rs -> {
                    others.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(new String[]{rs.getString(2), rs.getString(3)});
                });
        List<Collision> out = new ArrayList<>();
        for (Row r : rows) {
            for (String[] o : others.getOrDefault(r.ticker().toUpperCase(Locale.ROOT), List.of())) {
                out.add(new Collision(r.ticker(), r.type(), o[0], o[1]));
            }
        }
        return out;
    }

    private ForeignCuration loadCuration() {
        try {
            return ForeignCuration.load(dir.resolve("curadoria.json"), mapper);
        } catch (IOException e) {
            throw new IllegalStateException("Curadoria ilegível (" + dir.resolve("curadoria.json") + "): "
                    + e.getMessage(), e);
        }
    }

    private Path writePreview(LocalDate today, boolean write, List<Row> rows, List<Excluded> excluded, List<Gone> gone,
                              List<Collision> collisions, List<ReitDivergence> divergences, ForeignCuration curation,
                              int curated, List<String> problems) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("geradoEm", LocalDateTime.now().withNano(0).toString());
        root.put("fotografia", today.toString());
        root.put("gravou", write);
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String t : TYPES) {
            summary.put(t, count(rows, t));
        }
        summary.put("excluidos", excluded.size());
        summary.put("semCategoria", rows.stream().filter(r -> r.category() == null).count());
        Map<String, Long> byAction = new TreeMap<>();
        rows.forEach(r -> byAction.merge(r.action(), 1L, Long::sum));
        summary.put("acoes", byAction);
        Map<String, Long> bySource = new TreeMap<>();
        rows.forEach(r -> bySource.merge(r.type() + "/" + r.categorySource(), 1L, Long::sum));
        summary.put("origemCategoria", bySource);
        root.put("resumo", summary);
        root.put("ativos", rows);
        root.put("excluidos", excluded);
        root.put("deslistados", gone);
        root.put("colisoes", collisions);
        root.put("divergenciasReit", divergences);
        List<CategoryInfo> cats = new ArrayList<>();
        categoriesByType().forEach((t, m) -> m.forEach((c, desc) -> cats.add(new CategoryInfo(t, c, desc))));
        cats.sort((a, b) -> (a.type() + a.category()).compareTo(b.type() + b.category()));
        root.put("categorias", cats);
        Map<String, Object> cur = new LinkedHashMap<>();
        cur.put("arquivo", curation.file() == null ? null : curation.file().getFileName().toString());
        cur.put("itens", curation.items().size());
        cur.put("aplicados", curated);
        cur.put("problemas", problems);
        root.put("curadoria", cur);
        Path out = dir.resolve("catalogo-previa.json");
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("catalogo-previa.json.tmp");
            mapper.writeValue(tmp.toFile(), root);
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Não gravou a prévia do catálogo do exterior em " + out, e);
        }
        return out;
    }

    // ------------------------------------------------------------------ util

    private static boolean differs(Existing e, Draft d) {
        return !Objects.equals(e.name(), d.name) || !Objects.equals(e.exchange(), d.listing.exchange().name())
                || (d.category != null && !Objects.equals(e.category(), d.category)) || !e.hasListing();
    }

    private static int count(List<Row> rows, String type) {
        return (int) rows.stream().filter(r -> r.type().equals(type)).count();
    }

    private static String key(String ticker, String type) {
        return (ticker == null ? "" : ticker.toUpperCase(Locale.ROOT)) + "|" + type;
    }

    private static String str(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
