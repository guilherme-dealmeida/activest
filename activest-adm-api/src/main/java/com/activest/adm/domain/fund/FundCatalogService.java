package com.activest.adm.domain.fund;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.PublicFileCache;
import com.activest.adm.integration.cvm.CvmFundFiles;
import com.activest.domain.asset.Asset;
import com.activest.domain.asset.PriceSource;
import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.activest.domain.fund.FundCategory;
import com.activest.domain.fund.FundDailyReportReader;
import com.activest.domain.fund.FundKeys;
import com.activest.domain.fund.FundLabel;
import com.activest.domain.portfolio.OpenPositions;
import com.activest.domain.task.TaskRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Fundos de investimento da CVM no catálogo (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, F2–F4):
 *
 * <ul>
 *   <li><b>cadastro</b> ({@link #syncCatalog}): os cadastros da CVM viram ativos do tipo FUNDO_INVESTIMENTO e linhas de
 *   {@code investment_funds}/{@code investment_fund_names}, com rótulo (C1), vivo (C2), categoria (C3) e a última cota
 *   (C4) — gravação em lote pelo JDBC (~53 mil fundos);</li>
 *   <li><b>rodada diária</b> ({@link #runDaily}, tarefa {@code FUND_QUOTES}): cadastros e informe do mês corrente e dos
 *   12 anteriores (D9), última cota de todos os vivos e a série dos fundos em carteira, com os avisos de fonte;</li>
 *   <li><b>carga inicial</b> ({@link #bootstrap}): o informe inteiro desde 2010 para saber quem já reportou (D2) — sem
 *   série (D5);</li>
 *   <li><b>série sob demanda</b> ({@link #loadSeries}): a série completa de um fundo quando ele entra numa carteira
 *   (C11), pela fila única dos pedidos da carteira.</li>
 * </ul>
 */
@Slf4j
@Service
public class FundCatalogService {

    static final String ROUTINE = "Fundos de investimento";
    static final String SOURCE = "CVM (informe diário dos fundos)";
    /** Cota informada nos últimos 30 dias = vivo, mesmo com o cadastro dizendo cancelado (C2, D26). */
    static final int ALIVE_WINDOW_DAYS = 30;
    /** Retificações da CVM: o mês corrente e os 12 anteriores (D9). */
    static final int REVISION_MONTHS = 12;
    static final int STALE_BUSINESS_DAYS = 2;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String TYPE = "FUNDO_INVESTIMENTO";

    private static final String UPSERT_PRICE = """
        INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
        VALUES (?, ?, ?, 'CVM')
        ON CONFLICT (asset_id, reference_date) DO UPDATE
           SET close_price = EXCLUDED.close_price, source = 'CVM', updated_at = now()
         WHERE asset_price_history.close_price IS DISTINCT FROM EXCLUDED.close_price""";

    /** A última cota informada de uma chave. */
    record Last(LocalDate date, BigDecimal quote) {
    }

    /** Resumo de uma rodada. */
    public record Result(int funds, int created, int alive, int quotes, int seriesRows, LocalDate lastDate,
                         String sourceProblem, LocalDate staleSince) {
        @Override
        public String toString() {
            return funds + " fundos (" + created + " novos, " + alive + " vivos), " + quotes + " cotas atuais, "
                    + seriesRows + " linhas de série" + (lastDate == null ? "" : ", última cota de " + lastDate)
                    + (sourceProblem == null ? "" : ", fonte: " + sourceProblem)
                    + (staleSince == null ? "" : ", fonte sem dado novo desde " + staleSince);
        }
    }

    private final CvmFundFiles files;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OpenPositions openPositions;
    private final CatalogChangePublisher catalogPublisher;
    private final AdminNotices notices;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    public FundCatalogService(CvmFundFiles files, JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                              OpenPositions openPositions, CatalogChangePublisher catalogPublisher,
                              AdminNotices notices) {
        this(files, jdbc, transactionManager, openPositions, catalogPublisher, notices, Clock.system(ZONE));
    }

    FundCatalogService(CvmFundFiles files, JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                       OpenPositions openPositions, CatalogChangePublisher catalogPublisher, AdminNotices notices,
                       Clock clock) {
        this.files = files;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.openPositions = openPositions;
        this.catalogPublisher = catalogPublisher;
        this.notices = notices;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // rodadas
    // ------------------------------------------------------------------

    /**
     * Carga inicial (BOOTSTRAP): baixa o que faltar do informe desde 2010, varre todos os ZIPs para saber quem já
     * reportou (D2) e a última cota de cada um, e cadastra. Sem série (D5). Devolve nulo se outra rodada já corre.
     */
    public Result bootstrap(String reason) {
        if (!lock.tryLock()) {
            return null;
        }
        try {
            String problem = refreshRegistries(true);
            LocalDate today = LocalDate.now(clock);
            for (int y = CvmFundFiles.FIRST_YEAR; y < CvmFundFiles.FIRST_MONTHLY.getYear(); y++) {
                TaskRun.checkStop();
                problem = merge(problem, describe(files.yearly(y)));
            }
            for (YearMonth m = CvmFundFiles.FIRST_MONTHLY; !m.isAfter(YearMonth.from(today)); m = m.plusMonths(1)) {
                TaskRun.checkStop();
                boolean recent = !m.isBefore(YearMonth.from(today).minusMonths(REVISION_MONTHS));
                problem = merge(problem, describe(files.monthly(m, recent)));
            }
            Map<String, Last> reported = new HashMap<>();
            for (Path zip : files.localDailyFiles()) {
                TaskRun.checkStop();
                scan(zip, reported, null, null);
            }
            log.info("Fundos ({}): informe varrido, {} chaves desde {}", reason, reported.size(),
                    CvmFundFiles.FIRST_YEAR);
            SyncResult sync = syncCatalog(reported, true);
            return new Result(sync.funds, sync.created, sync.alive, sync.quotes, 0, maxDate(reported), problem, null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Rodada diária (tarefa FUND_QUOTES): cadastros, informe do mês corrente e dos 12 anteriores (ou desde
     * {@code fromMonth}), última cota de todos os vivos, série dos fundos em carteira e aviso à carteira.
     */
    public Result runDaily(YearMonth fromMonth, String reason) {
        if (!lock.tryLock()) {
            return null;
        }
        try {
            LocalDate today = LocalDate.now(clock);
            YearMonth current = YearMonth.from(today);
            YearMonth from = fromMonth != null ? fromMonth : current.minusMonths(REVISION_MONTHS);
            String problem = refreshRegistries(true);
            boolean sourceAnswered = false;
            List<Path> zips = new ArrayList<>();
            for (YearMonth m = from; !m.isAfter(current); m = m.plusMonths(1)) {
                TaskRun.checkStop();
                if (m.isBefore(CvmFundFiles.FIRST_MONTHLY)) {
                    PublicFileCache.Result r = files.yearly(m.getYear());
                    r.path().ifPresent(zips::add);
                    m = YearMonth.of(m.getYear(), 12);
                    continue;
                }
                PublicFileCache.Result r = files.monthly(m, true);
                problem = merge(problem, describe(r));
                sourceAnswered |= r.current();
                r.path().ifPresent(zips::add);
            }
            if (zips.isEmpty()) {
                notices.sourceUnavailable(ROUTINE, SOURCE, problem == null ? "sem arquivo do informe" : problem);
                throw new PublicFileCache.UnavailableException("Informe diário dos fundos indisponível: " + problem, null);
            }
            Map<Long, String> held = held();
            Set<String> heldKeys = new HashSet<>(held.values());
            Map<String, Last> reported = new HashMap<>();
            Map<String, List<FundDailyReportReader.Row>> series = new HashMap<>();
            for (Path zip : zips.stream().distinct().toList()) {
                TaskRun.checkStop();
                scan(zip, reported, heldKeys::contains, series);
            }
            SyncResult sync = syncCatalog(reported, false);
            int seriesRows = writeSeries(series, sync.idsByKey);
            LocalDate lastDate = maxDate(reported);
            LocalDate staleSince = sourceAnswered ? staleness(lastDate, reason) : null;
            if (problem != null && !sourceAnswered) {
                notices.sourceUnavailable(ROUTINE, SOURCE, problem);
            }
            if (!held.isEmpty()) {
                catalogPublisher.publish(new CatalogChange(CatalogChange.Origin.FECHAMENTO, held.keySet()));
            }
            Result result = new Result(sync.funds, sync.created, sync.alive, sync.quotes, seriesRows, lastDate,
                    sourceAnswered ? null : problem, staleSince);
            log.info("Fundos ({}): {}", reason, result);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /** A rodada diária deve recuperar na subida: nenhuma cota de fundo ou a última com mais de 2 dias úteis. */
    public boolean behind() {
        LocalDate last = jdbc.queryForObject("SELECT MAX(last_quote_on) FROM investment_funds", LocalDate.class);
        if (last == null) {
            return true;
        }
        LocalDate today = LocalDate.now(clock);
        return !last.plusDays(1).isAfter(today.minusDays(1))
                && BusinessCalendar.businessDaysBetween(last.plusDays(1), today.minusDays(1)) > STALE_BUSINESS_DAYS;
    }

    /**
     * Série completa de um fundo desde 2010, dos ZIPs em disco (C11): só as datas que faltam ou mudaram. Chamada pela
     * fila única dos pedidos da carteira; ativo que não é fundo é ignorado. Devolve as linhas gravadas.
     */
    public int loadSeries(Long assetId, String reason) {
        Map<String, Object> asset = jdbc.queryForList("""
                SELECT a.ticker, t.price_source FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                 WHERE a.id = ?""", assetId).stream().findFirst().orElse(null);
        if (asset == null || !PriceSource.CVM.name().equals(asset.get("price_source"))) {
            return 0;
        }
        String key = (String) asset.get("ticker");
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asset_price_history WHERE asset_id = ? AND source = 'CVM'", Integer.class, assetId);
        if (existing != null && existing > 0 && seriesComplete(assetId)) {
            log.debug("Série do fundo {} ({}): já carregada", key, reason);
            return 0;
        }
        long started = System.currentTimeMillis();
        Map<String, List<FundDailyReportReader.Row>> series = new HashMap<>();
        for (Path zip : files.localDailyFiles()) {
            // filtro no leitor: as linhas de outros fundos não chegam a ter números convertidos
            FundDailyReportReader.read(zip, key::equals, row -> {
                if (FundDailyReportReader.usableQuote(row.quote())) {
                    series.computeIfAbsent(row.key(), k -> new ArrayList<>()).add(row);
                }
            });
        }
        int rows = writeSeries(series, Map.of(key, assetId));
        log.info("Série do fundo {} ({}): {} linhas gravadas ({} já existiam) em {}s", key, reason, rows, existing,
                (System.currentTimeMillis() - started) / 1000);
        if (rows > 0) {
            catalogPublisher.publish(new CatalogChange(CatalogChange.Origin.FECHAMENTO, Set.of(assetId)));
        }
        return rows;
    }

    /**
     * A série já foi carregada inteira? A rodada diária só grava dentro da janela de revisão (mês corrente e 12
     * anteriores): cota mais antiga que a janela só existe se a carga completa já rodou; fundo que começou dentro da
     * janela tem a série toda pela rodada diária. Sem coluna de estado (o dado gravado é o estado).
     */
    private boolean seriesComplete(Long assetId) {
        LocalDate windowStart = YearMonth.from(LocalDate.now(clock)).minusMonths(REVISION_MONTHS).atDay(1);
        LocalDate min = jdbc.queryForObject(
                "SELECT MIN(reference_date) FROM asset_price_history WHERE asset_id = ? AND source = 'CVM'",
                LocalDate.class, assetId);
        if (min != null && min.isBefore(windowStart)) {
            return true;
        }
        LocalDate started = jdbc.queryForList("SELECT started_on FROM investment_funds WHERE asset_id = ?",
                LocalDate.class, assetId).stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return started != null && !started.isBefore(windowStart);
    }

    // ------------------------------------------------------------------
    // leitura
    // ------------------------------------------------------------------

    /** Varre um ZIP: última cota de cada chave e, para as chaves de {@code seriesKeys}, as linhas. */
    static void scan(Path zip, Map<String, Last> reported, Predicate<String> seriesKeys,
                     Map<String, List<FundDailyReportReader.Row>> series) {
        FundDailyReportReader.read(zip, null, row -> {
            if (!FundDailyReportReader.usableQuote(row.quote())) {
                return;
            }
            reported.merge(row.key(), new Last(row.date(), row.quote()),
                    (a, b) -> b.date().isAfter(a.date()) ? b : a);
            if (series != null && seriesKeys != null && seriesKeys.test(row.key())) {
                series.computeIfAbsent(row.key(), k -> new ArrayList<>()).add(row);
            }
        });
    }

    private String refreshRegistries(boolean refresh) {
        String problem = describe(files.registry(refresh));
        problem = merge(problem, describe(files.registryHistory(refresh)));
        return merge(problem, describe(files.oldRegistry(refresh)));
    }

    private static String describe(PublicFileCache.Result r) {
        if (!r.failed()) {
            return null;
        }
        return r.outcome() + (r.detail() == null ? "" : " " + r.detail())
                + (r.file() == null ? " (sem cópia em disco)" : " (usada a cópia em disco)");
    }

    private static String merge(String a, String b) {
        return a == null ? b : b == null ? a : a + "; " + b;
    }

    private static LocalDate maxDate(Map<String, Last> reported) {
        return reported.values().stream().map(Last::date).max(LocalDate::compareTo).orElse(null);
    }

    private LocalDate staleness(LocalDate lastDate, String reason) {
        if (lastDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        int missing = lastDate.plusDays(1).isAfter(today.minusDays(1)) ? 0
                : BusinessCalendar.businessDaysBetween(lastDate.plusDays(1), today.minusDays(1));
        if (missing > STALE_BUSINESS_DAYS) {
            log.warn("Fundos ({}): a CVM respondeu, mas a cota mais recente é de {} ({} dias úteis)", reason, lastDate,
                    missing);
            notices.sourceStale(ROUTINE, SOURCE, lastDate, missing);
            return lastDate;
        }
        notices.sourceCurrent(ROUTINE, SOURCE);
        return null;
    }

    /** Fundos em carteira: id → ticker. */
    private Map<Long, String> held() {
        Map<Long, String> held = new HashMap<>();
        for (Asset a : openPositions.assetsWithShares(PriceSource.CVM)) {
            held.put(a.getId(), a.getTicker());
        }
        return held;
    }

    // ------------------------------------------------------------------
    // gravação
    // ------------------------------------------------------------------

    record SyncResult(int funds, int created, int alive, int quotes, Map<String, Long> idsByKey) {
    }

    /**
     * Cadastra/atualiza os fundos. Universo: os cadastros da CVM, as chaves do informe ({@code reported}) e os fundos
     * que já estão no catálogo. {@code rebuildNames}: regrava os nomes para a busca (sempre que o histórico de nomes
     * foi lido de novo, ou quando a tabela está vazia).
     */
    SyncResult syncCatalog(Map<String, Last> reported, boolean rebuildNames) {
        Long typeId = jdbc.queryForObject("SELECT id FROM asset_types WHERE name = ?", Long.class, TYPE);
        Map<String, Long> categories = new HashMap<>();
        jdbc.query("SELECT id, name FROM asset_categories WHERE asset_type_id = ?",
                rs -> { categories.put(rs.getString("name"), rs.getLong("id")); }, typeId);
        Map<String, Long> idsByKey = new HashMap<>();
        jdbc.query("SELECT id, ticker FROM assets WHERE asset_type_id = ?",
                rs -> { idsByKey.put(rs.getString("ticker"), rs.getLong("id")); }, typeId);

        Set<String> universe = new LinkedHashSet<>(reported.keySet());
        universe.addAll(idsByKey.keySet());
        FundRegistry registry = FundRegistry.load(files.dir().resolve("registro_fundo_classe.zip"),
                files.dir().resolve("cad_fi_hist.zip"), files.dir().resolve("cad_fi.csv"), universe);
        Map<String, FundRecord> records = registry.records();
        LocalDate today = LocalDate.now(clock);
        LocalDate aliveSince = today.minusDays(ALIVE_WINDOW_DAYS);

        List<FundRecord> fresh = records.values().stream().filter(r -> !idsByKey.containsKey(r.key())).toList();
        Integer namesCount = jdbc.queryForObject("SELECT COUNT(*) FROM investment_fund_names", Integer.class);
        // nomes (registro, a busca usa search_text): todos quando pedido ou vazio; senão só os dos fundos novos
        boolean allNames = rebuildNames || namesCount == null || namesCount == 0;
        boolean names = allNames || !fresh.isEmpty();
        int[] alive = {0};
        int[] quotes = {0};
        tx.executeWithoutResult(s -> {
            // ativos novos
            jdbc.batchUpdate("""
                    INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active, currency_code)
                    VALUES (?, ?, ?, ?, false, 'BRL') ON CONFLICT ON CONSTRAINT uk_assets_ticker_type DO NOTHING""",
                    fresh, 1000, (ps, r) -> {
                        ps.setString(1, r.key());
                        ps.setString(2, truncate(r.legalName(), 255));
                        ps.setLong(3, typeId);
                        ps.setLong(4, categories.get(FundCategory.of(r.anbimaClassification(), r.cvmClassification())));
                    });
            jdbc.query("SELECT id, ticker FROM assets WHERE asset_type_id = ?",
                    rs -> { idsByKey.put(rs.getString("ticker"), rs.getLong("id")); }, typeId);

            List<FundRecord> all = new ArrayList<>(records.values());
            // ativo: nome, categoria, vivo e última cota
            // só o que mudou (D13): a rodada diária repassa os ~55 mil fundos e quase nada muda de um dia para o outro
            jdbc.batchUpdate("""
                    UPDATE assets SET name = n.name, asset_category_id = n.category, active = n.active,
                           current_price = COALESCE(n.price, assets.current_price),
                           price_updated_at = COALESCE(n.price_at, assets.price_updated_at)
                      FROM (SELECT ?::varchar AS name, ?::bigint AS category, ?::boolean AS active,
                                   ?::numeric AS price, ?::timestamp AS price_at, ?::bigint AS id) n
                     WHERE assets.id = n.id
                       AND (assets.name, assets.asset_category_id, assets.active,
                            COALESCE(n.price, assets.current_price), COALESCE(n.price_at, assets.price_updated_at))
                           IS DISTINCT FROM
                           (n.name, n.category, n.active, assets.current_price, assets.price_updated_at)""",
                    all, 1000, (ps, r) -> {
                Last last = reported.get(r.key());
                boolean isAlive = r.aliveByStatus() || (last != null && !last.date().isBefore(aliveSince));
                if (isAlive) {
                    alive[0]++;
                }
                if (last != null) {
                    quotes[0]++;
                }
                ps.setString(1, truncate(r.legalName(), 255));
                ps.setLong(2, categories.get(FundCategory.of(r.anbimaClassification(), r.cvmClassification())));
                ps.setBoolean(3, isAlive);
                ps.setBigDecimal(4, last == null ? null : last.quote().setScale(8, RoundingMode.HALF_EVEN));
                ps.setTimestamp(5, last == null ? null : Timestamp.valueOf(last.date().atStartOfDay()));
                ps.setLong(6, idsByKey.get(r.key()));
            });
            // cadastro do fundo
            jdbc.batchUpdate("""
                    INSERT INTO investment_funds (asset_id, cnpj, subclass_id, parent_asset_id, short_name, trade_name,
                        fund_kind, cvm_classification, anbima_classification, target_audience, exclusive, open_ended,
                        long_term_tax, manager_name, manager_cnpj, administrator_name, administrator_cnpj, cvm_status,
                        cvm_status_since, started_on, net_worth, net_worth_date, last_quote_on, search_text)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (asset_id) DO UPDATE SET
                        cnpj = EXCLUDED.cnpj, subclass_id = EXCLUDED.subclass_id, parent_asset_id = EXCLUDED.parent_asset_id,
                        short_name = EXCLUDED.short_name, trade_name = EXCLUDED.trade_name, fund_kind = EXCLUDED.fund_kind,
                        cvm_classification = EXCLUDED.cvm_classification,
                        anbima_classification = EXCLUDED.anbima_classification,
                        target_audience = EXCLUDED.target_audience, exclusive = EXCLUDED.exclusive,
                        open_ended = EXCLUDED.open_ended, long_term_tax = EXCLUDED.long_term_tax,
                        manager_name = EXCLUDED.manager_name, manager_cnpj = EXCLUDED.manager_cnpj,
                        administrator_name = EXCLUDED.administrator_name,
                        administrator_cnpj = EXCLUDED.administrator_cnpj, cvm_status = EXCLUDED.cvm_status,
                        cvm_status_since = EXCLUDED.cvm_status_since, started_on = EXCLUDED.started_on,
                        net_worth = EXCLUDED.net_worth, net_worth_date = EXCLUDED.net_worth_date,
                        last_quote_on = COALESCE(EXCLUDED.last_quote_on, investment_funds.last_quote_on),
                        search_text = EXCLUDED.search_text, updated_at = now()
                    WHERE (investment_funds.cnpj, investment_funds.subclass_id, investment_funds.parent_asset_id,
                           investment_funds.short_name, investment_funds.trade_name, investment_funds.fund_kind,
                           investment_funds.cvm_classification, investment_funds.anbima_classification,
                           investment_funds.target_audience, investment_funds.exclusive, investment_funds.open_ended,
                           investment_funds.long_term_tax, investment_funds.manager_name, investment_funds.manager_cnpj,
                           investment_funds.administrator_name, investment_funds.administrator_cnpj,
                           investment_funds.cvm_status, investment_funds.cvm_status_since, investment_funds.started_on,
                           investment_funds.net_worth, investment_funds.net_worth_date,
                           COALESCE(EXCLUDED.last_quote_on, investment_funds.last_quote_on), investment_funds.search_text)
                          IS DISTINCT FROM
                          (EXCLUDED.cnpj, EXCLUDED.subclass_id, EXCLUDED.parent_asset_id, EXCLUDED.short_name,
                           EXCLUDED.trade_name, EXCLUDED.fund_kind, EXCLUDED.cvm_classification,
                           EXCLUDED.anbima_classification, EXCLUDED.target_audience, EXCLUDED.exclusive,
                           EXCLUDED.open_ended, EXCLUDED.long_term_tax, EXCLUDED.manager_name, EXCLUDED.manager_cnpj,
                           EXCLUDED.administrator_name, EXCLUDED.administrator_cnpj, EXCLUDED.cvm_status,
                           EXCLUDED.cvm_status_since, EXCLUDED.started_on, EXCLUDED.net_worth, EXCLUDED.net_worth_date,
                           investment_funds.last_quote_on, EXCLUDED.search_text)""", all, 1000, (ps, r) -> {
                Last last = reported.get(r.key());
                String shortName = FundLabel.of(r.tradeName(), r.legalName());
                int i = 1;
                ps.setLong(i++, idsByKey.get(r.key()));
                ps.setString(i++, r.cnpj());
                ps.setString(i++, truncate(r.subclassId(), 20));
                ps.setObject(i++, r.parentKey() == null ? null : idsByKey.get(r.parentKey()));
                ps.setString(i++, truncate(shortName.isEmpty() ? r.key() : shortName, 160));
                ps.setString(i++, truncate(r.tradeName(), 200));
                ps.setString(i++, truncate(r.fundKind(), 20));
                ps.setString(i++, truncate(r.cvmClassification(), 40));
                ps.setString(i++, truncate(r.anbimaClassification(), 80));
                ps.setString(i++, truncate(r.targetAudience(), 20));
                ps.setObject(i++, r.exclusive());
                ps.setObject(i++, r.openEnded());
                ps.setObject(i++, r.longTermTax());
                ps.setString(i++, truncate(r.managerName(), 200));
                ps.setString(i++, r.managerCnpj());
                ps.setString(i++, truncate(r.administratorName(), 200));
                ps.setString(i++, r.administratorCnpj());
                ps.setString(i++, truncate(r.status(), 40));
                ps.setDate(i++, r.statusSince() == null ? null : Date.valueOf(r.statusSince()));
                ps.setDate(i++, r.startedOn() == null ? null : Date.valueOf(r.startedOn()));
                ps.setBigDecimal(i++, r.netWorth());
                ps.setDate(i++, r.netWorthDate() == null ? null : Date.valueOf(r.netWorthDate()));
                ps.setDate(i++, last == null ? null : Date.valueOf(last.date()));
                ps.setString(i, searchText(r, shortName, registry.names(r.cnpj())));
            });
            if (names) {
                if (allNames) {
                    jdbc.update("""
                            DELETE FROM investment_fund_names n USING assets a
                             WHERE a.id = n.asset_id AND a.asset_type_id = ?""", typeId);
                }
                List<Object[]> rows = new ArrayList<>();
                for (FundRecord r : allNames ? all : fresh) {
                    Long id = idsByKey.get(r.key());
                    rows.add(new Object[]{id, truncate(r.legalName(), 250), "LEGAL", null, null});
                    if (r.subclassId() == null) {
                        for (FundRegistry.Name n : registry.names(r.cnpj())) {
                            rows.add(new Object[]{id, n.name(), n.trade() ? "TRADE" : "LEGAL",
                                    n.from() == null ? null : Date.valueOf(n.from()),
                                    n.to() == null ? null : Date.valueOf(n.to())});
                        }
                    }
                }
                jdbc.batchUpdate("INSERT INTO investment_fund_names (asset_id, name, kind, valid_from, valid_to) "
                        + "VALUES (?, ?, ?, ?, ?)", rows);
            }
        });
        log.info("Fundos: {} no catálogo ({} novos, {} vivos, {} com cota), nomes {}", records.size(), fresh.size(),
                alive[0], quotes[0], allNames ? "regravados" : names ? "dos novos gravados" : "mantidos");
        return new SyncResult(records.size(), fresh.size(), alive[0], quotes[0], idsByKey);
    }

    /** Grava as linhas da série (só o que falta ou mudou). */
    private int writeSeries(Map<String, List<FundDailyReportReader.Row>> series, Map<String, Long> idsByKey) {
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<String, List<FundDailyReportReader.Row>> e : series.entrySet()) {
            Long id = idsByKey.get(e.getKey());
            if (id == null) {
                continue;
            }
            Map<LocalDate, BigDecimal> byDate = new HashMap<>();
            for (FundDailyReportReader.Row r : e.getValue()) {
                byDate.put(r.date(), r.quote().setScale(8, RoundingMode.HALF_EVEN));
            }
            byDate.forEach((d, q) -> rows.add(new Object[]{id, Date.valueOf(d), q}));
        }
        if (rows.isEmpty()) {
            return 0;
        }
        int[][] counts = tx.execute(s -> jdbc.batchUpdate(UPSERT_PRICE, rows, 1000, (ps, r) -> {
            ps.setLong(1, (Long) r[0]);
            ps.setDate(2, (Date) r[1]);
            ps.setBigDecimal(3, (BigDecimal) r[2]);
        }));
        int written = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                written += Math.max(c, 0);
            }
        }
        return written;
    }

    /** CNPJ (com e sem máscara) e os nomes, sem acento e em maiúsculas, para a busca (D25). */
    static String searchText(FundRecord r, String shortName, List<FundRegistry.Name> names) {
        Set<String> parts = new LinkedHashSet<>();
        parts.add(r.cnpj());
        parts.add(FundKeys.format(r.cnpj()));
        if (r.subclassId() != null) {
            parts.add(r.subclassId());
        }
        parts.add(FundKeys.normalize(shortName));
        parts.add(FundKeys.normalize(r.legalName()));
        if (r.tradeName() != null) {
            parts.add(FundKeys.normalize(r.tradeName()));
        }
        if (r.subclassId() == null) {
            for (FundRegistry.Name n : names) {
                parts.add(FundKeys.normalize(n.name()));
            }
        }
        return parts.stream().filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" | "));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
