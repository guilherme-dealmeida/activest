package com.activest.adm.domain.foreign;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.foreign.ForeignEventsProvider;
import com.activest.adm.integration.foreign.ForeignEventsProvider.Dividend;
import com.activest.adm.integration.foreign.ForeignEventsProvider.Split;
import com.activest.adm.integration.foreign.ForeignQuotesProvider;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.activest.domain.dividend.CashEventWriter;
import com.activest.domain.task.TaskRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Proventos e desdobramentos dos ativos internacionais (docs/planos/PLANO_PROVENTOS_INTERNACIONAIS.md), um mês do
 * mercado inteiro por vez ({@link ForeignEventsProvider}, hoje o Massive).
 *
 * <ul>
 *   <li>Só tickers do catálogo (Stock, REIT, ETF internacional), casados com o papel <b>listado na data-com</b>
 *   ({@link ForeignListings}: deslistado até a saída, senão o vivo), e só na moeda do ativo (USD) — D3.</li>
 *   <li>Data-com = o último pregão antes da ex-date (série {@code MASSIVE}; sem pregão carregado, o dia útil anterior)
 *   — D5. Pagamento = {@code pay_date}; aprovação = {@code declaration_date} (D6).</li>
 *   <li>Tipo DIVIDENDO, com o tipo da fonte no {@code label} (D4). O evento é casado pelo id da fonte
 *   ({@code source_ref}): uma correção de valor ou de data atualiza o mesmo evento, sem duplicar.</li>
 *   <li>Desdobramento → {@code asset_stock_events} (D9): razão = para ÷ de; maior que 1 = DESDOBRAMENTO com fator
 *   (razão − 1) × 100; menor = GRUPAMENTO com a razão.</li>
 *   <li>Ativos que mudaram avisam a carteira ({@code CatalogChange.PROVENTOS}): a app-api gera os proventos em dólar.</li>
 * </ul>
 */
@Slf4j
@Service
public class ForeignCorporateEventsService {

    static final String CURRENCY = "USD";
    private static final String ROUTINE = "Proventos dos ativos internacionais";
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static final String INSERT_CASH = """
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, approved_on,
                                           source, source_ref, seq, fetched_at)
            VALUES (?, 'DIVIDENDO', ?, ?, ?, ?, ?, 'MASSIVE', ?, ?, NOW())
            ON CONFLICT (asset_id, type, data_com, value_per_share, seq) DO NOTHING
            """;
    private static final String UPDATE_CASH = """
            UPDATE asset_cash_events SET asset_id = ?, label = ?, data_com = ?, payment_date = ?, value_per_share = ?,
                                         approved_on = ?, fetched_at = NOW()
            WHERE id = ?
            """;
    private static final String INSERT_SPLIT = """
            INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source, fetched_at)
            VALUES (?, ?, ?, ?, 'MASSIVE', NOW())
            ON CONFLICT (asset_id, type, data_com, factor) DO NOTHING
            """;

    private final ForeignEventsProvider provider;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired(required = false)
    CatalogChangePublisher catalogChangePublisher;

    @Autowired(required = false)
    AdminNotices notices;

    @Autowired
    public ForeignCorporateEventsService(ForeignEventsProvider provider, JdbcTemplate jdbc) {
        this(provider, jdbc, Clock.systemDefaultZone());
    }

    ForeignCorporateEventsService(ForeignEventsProvider provider, JdbcTemplate jdbc, Clock clock) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Resultado da rodada.
     *
     * @param otherCurrency proventos de papel do catálogo em outra moeda (ignorados)
     * @param notInCatalog  eventos de ticker fora do catálogo (OTC, fundos, ou papel antes de entrar)
     */
    public record Result(YearMonth from, YearMonth to, int months, int pages, int dividendsRead, int otherCurrency,
                         int notInCatalog, int inserted, int updated, int unchanged, int splitsRead, int splitsInserted,
                         int assetsChanged) {
    }

    /** Rodada diária (D8): os meses da janela de ex-date de −30 a +90 dias, sempre perguntando de novo. */
    public Result importRecent(String reason) {
        LocalDate today = LocalDate.now(clock.withZone(NEW_YORK));
        return importMonths(YearMonth.from(today.minusDays(30)), YearMonth.from(today.plusDays(90)), true, reason);
    }

    /**
     * Meses de {@code from} a {@code to}. Sem {@code refreshAll}, mês já encerrado há mais de 31 dias e guardado em
     * disco é relido do arquivo; os recentes perguntam de novo (anúncios e correções).
     *
     * @return nulo quando já há uma rodada em andamento
     */
    public Result importMonths(YearMonth from, YearMonth to, boolean refreshAll, String reason) {
        if (!running.tryLock()) {
            log.warn("Proventos internacionais ({}): já existe uma rodada em andamento; nada feito", reason);
            return null;
        }
        Set<Long> changed = new HashSet<>();
        try {
            LocalDate today = LocalDate.now(clock.withZone(NEW_YORK));
            Map<String, List<ForeignListings.Listed>> catalog = ForeignListings.load(jdbc, from.atDay(1).minusDays(7));
            TreeSet<LocalDate> sessions = new TreeSet<>(jdbc.queryForList(
                    "SELECT DISTINCT reference_date FROM asset_price_history WHERE source = 'MASSIVE'", LocalDate.class));
            Existing existing = existing();
            Counters c = new Counters();
            try {
                for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
                    TaskRun.checkStop(); // ponto de parada entre meses
                    boolean refresh = refreshAll || !m.atEndOfMonth().isBefore(today.minusDays(31));
                    ForeignEventsProvider.Month<Dividend> dm;
                    ForeignEventsProvider.Month<Split> sm;
                    try {
                        dm = provider.dividends(m, refresh);
                        sm = provider.splits(m, refresh);
                    } catch (ForeignQuotesProvider.AuthException | ForeignQuotesProvider.UnavailableException e) {
                        if (notices != null) {
                            notices.sourceUnavailable(ROUTINE, provider.sourceName(), e.getMessage());
                        }
                        throw e;
                    }
                    c.months++;
                    c.pages += dm.pages() + sm.pages();
                    writeDividends(dm.events(), catalog, sessions, existing, changed, c);
                    writeSplits(sm.events(), catalog, sessions, changed, c);
                    log.info("Proventos internacionais ({}): {} — {} proventos e {} desdobramentos na fonte{}; até aqui "
                                    + "{} novos, {} atualizados, {} desdobramentos novos", reason, m, dm.events().size(),
                            sm.events().size(), dm.fromDisk() ? " (arquivo em disco)" : "", c.inserted, c.updated,
                            c.splitsInserted);
                }
            } finally {
                publish(changed);
            }
            Result r = new Result(from, to, c.months, c.pages, c.dividendsRead, c.otherCurrency, c.notInCatalog,
                    c.inserted, c.updated, c.unchanged, c.splitsRead, c.splitsInserted, changed.size());
            log.info("Proventos internacionais ({}) concluído: {}", reason, r);
            if (notices != null) {
                notices.sourceCurrent(ROUTINE, provider.sourceName());
            }
            return r;
        } finally {
            running.unlock();
        }
    }

    /** Atrasado quando nenhum provento da fonte foi lido hoje nem ontem (recuperação na subida). */
    public boolean stale() {
        java.sql.Timestamp last = jdbc.queryForObject(
                "SELECT MAX(fetched_at) FROM asset_cash_events WHERE source = 'MASSIVE'", java.sql.Timestamp.class);
        return last == null || last.toLocalDateTime().toLocalDate().isBefore(LocalDate.now(clock).minusDays(1));
    }

    // ------------------------------------------------------------------ gravação

    private static final class Counters {
        int months;
        int pages;
        int dividendsRead;
        int otherCurrency;
        int notInCatalog;
        int inserted;
        int updated;
        int unchanged;
        int splitsRead;
        int splitsInserted;
    }

    /** Um evento da fonte já gravado. */
    private record Row(long id, long assetId, String label, LocalDate dataCom, LocalDate paymentDate, BigDecimal value,
                       LocalDate approvedOn) {
    }

    /** Os eventos da fonte já gravados (pelo id da fonte) e quantos há por chave de unicidade (para a parcela). */
    private record Existing(Map<String, Row> byRef, Map<String, Integer> seqByKey) {
    }

    private Existing existing() {
        Map<String, Row> byRef = new HashMap<>();
        Map<String, Integer> seq = new HashMap<>();
        jdbc.query("""
                SELECT id, asset_id, label, data_com, payment_date, value_per_share, approved_on, source_ref, seq
                FROM asset_cash_events WHERE source = 'MASSIVE'""", rs -> {
            Date pay = rs.getDate("payment_date");
            Date approved = rs.getDate("approved_on");
            Row row = new Row(rs.getLong("id"), rs.getLong("asset_id"), rs.getString("label"),
                    rs.getDate("data_com").toLocalDate(), pay == null ? null : pay.toLocalDate(),
                    rs.getBigDecimal("value_per_share"), approved == null ? null : approved.toLocalDate());
            String ref = rs.getString("source_ref");
            if (ref != null) {
                byRef.put(ref, row);
            }
            seq.merge(key(row.assetId(), row.dataCom(), row.value()), (int) rs.getShort("seq"), Math::max);
        });
        return new Existing(byRef, seq);
    }

    private static String key(long assetId, LocalDate dataCom, BigDecimal value) {
        return assetId + "|" + dataCom + "|" + value.stripTrailingZeros().toPlainString();
    }

    private void writeDividends(List<Dividend> events, Map<String, List<ForeignListings.Listed>> catalog,
                                TreeSet<LocalDate> sessions, Existing existing, Set<Long> changed, Counters c) {
        List<Object[]> inserts = new ArrayList<>();
        List<Long> insertAssets = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (Dividend d : events) {
            c.dividendsRead++;
            List<ForeignListings.Listed> candidates = catalog.get(d.ticker().toUpperCase());
            if (candidates == null) {
                c.notInCatalog++;
                continue;
            }
            LocalDate dataCom = dataCom(d.exDate(), sessions);
            ForeignListings.Listed asset = ForeignListings.pick(candidates, dataCom);
            if (asset == null) {
                c.notInCatalog++;
                continue;
            }
            if (!CURRENCY.equals(d.currency())) {
                c.otherCurrency++;
                continue;
            }
            BigDecimal value = d.amount().setScale(CashEventWriter.VALUE_SCALE, RoundingMode.HALF_UP);
            String label = d.type() == null || d.type().isBlank() ? "?" : d.type().trim();
            Row old = d.id() == null ? null : existing.byRef().get(d.id());
            if (old != null) {
                boolean same = old.assetId() == asset.id() && Objects.equals(old.label(), label)
                        && old.dataCom().equals(dataCom) && Objects.equals(old.paymentDate(), d.payDate())
                        && old.value().compareTo(value) == 0 && Objects.equals(old.approvedOn(), d.declarationDate());
                if (same) {
                    c.unchanged++;
                    continue;
                }
                updates.add(new Object[]{asset.id(), label, Date.valueOf(dataCom), sqlDate(d.payDate()), value,
                        sqlDate(d.declarationDate()), old.id()});
                existing.byRef().put(d.id(), new Row(old.id(), asset.id(), label, dataCom, d.payDate(), value,
                        d.declarationDate()));
                changed.add(asset.id());
                changed.add(old.assetId());
                c.updated++;
                continue;
            }
            int seq = existing.seqByKey().merge(key(asset.id(), dataCom, value), 1, Integer::sum);
            inserts.add(new Object[]{asset.id(), label, Date.valueOf(dataCom), sqlDate(d.payDate()), value,
                    sqlDate(d.declarationDate()), d.id(), (short) seq});
            insertAssets.add(asset.id());
            if (d.id() != null) {
                existing.byRef().put(d.id(), new Row(-1, asset.id(), label, dataCom, d.payDate(), value,
                        d.declarationDate()));
            }
        }
        if (!updates.isEmpty()) {
            jdbc.batchUpdate(UPDATE_CASH, updates);
        }
        if (!inserts.isEmpty()) {
            int[] counts = jdbc.batchUpdate(INSERT_CASH, inserts);
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] != 0) {
                    c.inserted++;
                    changed.add(insertAssets.get(i));
                }
            }
            // os ids recém-gravados entram no mapa (a próxima correção do mesmo evento atualiza, sem duplicar)
            reloadInsertedIds(existing, inserts);
        }
    }

    private void reloadInsertedIds(Existing existing, List<Object[]> inserts) {
        List<String> refs = new ArrayList<>();
        for (Object[] row : inserts) {
            if (row[6] != null) {
                refs.add((String) row[6]);
            }
        }
        for (int i = 0; i < refs.size(); i += 1000) {
            List<String> part = refs.subList(i, Math.min(refs.size(), i + 1000));
            String in = String.join(",", java.util.Collections.nCopies(part.size(), "?"));
            jdbc.query("SELECT id, source_ref FROM asset_cash_events WHERE source = 'MASSIVE' AND source_ref IN (" + in + ")",
                    rs -> {
                        Row r = existing.byRef().get(rs.getString("source_ref"));
                        if (r != null && r.id() < 0) {
                            existing.byRef().put(rs.getString("source_ref"), new Row(rs.getLong("id"), r.assetId(),
                                    r.label(), r.dataCom(), r.paymentDate(), r.value(), r.approvedOn()));
                        }
                    }, part.toArray());
        }
    }

    private void writeSplits(List<Split> events, Map<String, List<ForeignListings.Listed>> catalog,
                             TreeSet<LocalDate> sessions, Set<Long> changed, Counters c) {
        List<Object[]> rows = new ArrayList<>();
        List<Long> assets = new ArrayList<>();
        for (Split s : events) {
            c.splitsRead++;
            List<ForeignListings.Listed> candidates = catalog.get(s.ticker().toUpperCase());
            if (candidates == null) {
                continue;
            }
            LocalDate dataCom = dataCom(s.exDate(), sessions);
            ForeignListings.Listed asset = ForeignListings.pick(candidates, dataCom);
            if (asset == null) {
                continue;
            }
            Object[] typeAndFactor = typeAndFactor(s.from(), s.to());
            if (typeAndFactor == null) {
                continue;
            }
            rows.add(new Object[]{asset.id(), typeAndFactor[0], Date.valueOf(dataCom), typeAndFactor[1]});
            assets.add(asset.id());
        }
        if (rows.isEmpty()) {
            return;
        }
        int[] counts = jdbc.batchUpdate(INSERT_SPLIT, rows);
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] != 0) {
                c.splitsInserted++;
                changed.add(assets.get(i));
            }
        }
    }

    /**
     * Tipo e fator do {@code asset_stock_events} para "de {@code from} viram {@code to}": razão maior que 1 =
     * DESDOBRAMENTO com o percentual de cotas novas ((razão − 1) × 100); menor = GRUPAMENTO com a razão; igual = nada.
     */
    static Object[] typeAndFactor(BigDecimal from, BigDecimal to) {
        BigDecimal ratio = to.divide(from, MathContext.DECIMAL64);
        int cmp = ratio.compareTo(BigDecimal.ONE);
        if (cmp == 0) {
            return null;
        }
        if (cmp > 0) {
            return new Object[]{"DESDOBRAMENTO",
                    ratio.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(8, RoundingMode.HALF_UP)};
        }
        return new Object[]{"GRUPAMENTO", ratio.setScale(11, RoundingMode.HALF_UP)};
    }

    /** Data-com (D5): o último pregão carregado antes da ex-date; sem pregão carregado, o dia útil anterior. */
    static LocalDate dataCom(LocalDate exDate, TreeSet<LocalDate> sessions) {
        LocalDate previous = sessions.lower(exDate);
        LocalDate weekday = exDate.minusDays(1);
        while (weekday.getDayOfWeek() == DayOfWeek.SATURDAY || weekday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            weekday = weekday.minusDays(1);
        }
        // pregão carregado vale só quando a série cobre a data (ex-date dentro do período com pregões)
        if (previous != null && !sessions.isEmpty() && !exDate.isBefore(sessions.first())
                && !previous.isBefore(weekday.minusDays(7))) {
            return previous;
        }
        return weekday;
    }

    private static Date sqlDate(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private void publish(Set<Long> changed) {
        if (changed.isEmpty() || catalogChangePublisher == null) {
            return;
        }
        try {
            catalogChangePublisher.publish(new CatalogChange(CatalogChange.Origin.PROVENTOS, Set.copyOf(changed)));
        } catch (RuntimeException e) {
            log.warn("Proventos internacionais: aviso à carteira não publicado: {}", e.getMessage());
        }
    }
}
