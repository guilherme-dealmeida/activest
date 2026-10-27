package com.activest.adm.domain.currency;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.indices.IndexSourceException;
import com.activest.adm.integration.ptax.PtaxClient;
import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.activest.domain.currency.Currency;
import com.activest.domain.currency.CurrencyRateSource;
import com.activest.domain.currency.CurrencyRepository;
import com.activest.domain.task.TaskRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Câmbio das moedas cadastradas em {@code exchange_rates} (docs/planos/PLANO_CADASTRO_MOEDAS.md, M2 e 10.3). Catálogo
 * puro: só a adm-api grava; a app-api lê pelo {@code ExchangeRateService}.
 *
 * <ul>
 *   <li><b>Incremental</b> ({@link #importRecent}): por moeda ativa com fonte, do último boletim gravado menos a
 *   janela de revisão até hoje; moeda sem série começa em {@link #HISTORY_FROM}.</li>
 *   <li><b>Período</b> ({@link #importFrom}): da data informada até hoje (Run manual com atributos, D28).</li>
 * </ul>
 *
 * <p>Idempotente como os índices: igual é pulado, corrigido pela fonte é sobrescrito com log. Moeda que falha não
 * impede as outras. Uma carga por vez. Quando algo muda, publica "catálogo mudou" (CAMBIO) e a app-api descarta o
 * cache de câmbio e revaloriza as posições em moeda estrangeira.</p>
 *
 * <p><b>Fonte atrasada</b> (C1a, D27 — a regra do Tesouro): a fonte respondeu e o boletim mais recente está mais de
 * {@link #STALE_BUSINESS_DAYS} dias úteis atrás (exclusive nos dois lados) → aviso de administrador
 * {@code FONTE_DESATUALIZADA}, resolvido sozinho quando o boletim novo chega. Os cálculos seguem com o último câmbio.</p>
 */
@Slf4j
@Service
public class ExchangeRateImportService {

    public static final LocalDate HISTORY_FROM = LocalDate.of(2010, 1, 1);
    static final int REVISION_DAYS = 10;
    static final int STALE_BUSINESS_DAYS = 2;
    static final String ROUTINE = "Câmbio";
    static final String SOURCE = "Banco Central (PTAX)";
    private static final int RATE_SCALE = 8;
    private static final int MAX_CHANGE_LOGS = 20;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    AdminNotices notices;

    private final CurrencyRepository currencies;
    private final PtaxClient ptax;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CatalogChangePublisher publisher;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired
    public ExchangeRateImportService(CurrencyRepository currencies, PtaxClient ptax, JdbcTemplate jdbc,
                                     PlatformTransactionManager txm, CatalogChangePublisher publisher) {
        this(currencies, ptax, jdbc, txm, publisher, Clock.system(ZONE));
    }

    ExchangeRateImportService(CurrencyRepository currencies, PtaxClient ptax, JdbcTemplate jdbc,
                              PlatformTransactionManager txm, CatalogChangePublisher publisher, Clock clock) {
        this.currencies = currencies;
        this.ptax = ptax;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Uma moeda na rodada. {@code error} nulo = ok; {@code staleSince} = último boletim, quando a fonte atrasou. */
    public record CurrencyResult(String code, LocalDate from, int created, int updated, int unchanged,
                                 LocalDate changedFrom, LocalDate lastDate, LocalDate staleSince, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public record Result(List<CurrencyResult> currencies) {
        public int created() {
            return currencies.stream().mapToInt(CurrencyResult::created).sum();
        }

        public int updated() {
            return currencies.stream().mapToInt(CurrencyResult::updated).sum();
        }

        public List<String> failures() {
            return currencies.stream().filter(r -> !r.ok()).map(CurrencyResult::code).toList();
        }

        public Set<String> changed() {
            Set<String> s = new LinkedHashSet<>();
            currencies.stream().filter(r -> r.changedFrom() != null).forEach(r -> s.add(r.code()));
            return s;
        }

        @Override
        public String toString() {
            return currencies.size() + " moeda(s), " + created() + " boletins novos, " + updated() + " corrigidos"
                    + (failures().isEmpty() ? "" : ", falharam: " + String.join(", ", failures()))
                    + currencies.stream().filter(r -> r.staleSince() != null)
                        .map(r -> ", " + r.code() + " sem boletim novo desde " + r.staleSince()).reduce("", String::concat);
        }
    }

    /** Rodada incremental (13:30 e subida). Nulo se já há uma carga rodando. */
    public Result importRecent(String trigger) {
        return run(null, null, trigger);
    }

    /**
     * Carga de um período: de {@code from} até hoje.
     *
     * @param codes moedas; nulo ou vazio = todas as ativas com fonte
     * @throws IllegalArgumentException moeda desconhecida, inativa ou sem fonte
     */
    public Result importFrom(Collection<String> codes, LocalDate from, String trigger) {
        return run(codes, from, trigger);
    }

    /** Confere os códigos antes de disparar a carga em segundo plano. */
    public void validateCodes(Collection<String> codes) {
        select(codes);
    }

    /** Há moeda ativa sem o boletim do último dia útil antes de hoje? (recuperação na subida) */
    public boolean behind() {
        LocalDate today = LocalDate.now(clock);
        LocalDate expected = today.minusDays(1);
        while (!BusinessCalendar.isBusinessDay(expected)) {
            expected = expected.minusDays(1);
        }
        for (Currency c : select(null)) {
            Optional<LocalDate> last = lastDate(c.getCode());
            if (last.isEmpty() || last.get().isBefore(expected)) {
                return true;
            }
        }
        return false;
    }

    private Result run(Collection<String> codes, LocalDate fromDate, String trigger) {
        List<Currency> selected = select(codes);
        if (!running.tryLock()) {
            log.warn("Cambio ({}): ja existe uma carga rodando; nada feito", trigger);
            return null;
        }
        try {
            long started = System.currentTimeMillis();
            List<CurrencyResult> results = new ArrayList<>();
            for (Currency c : selected) {
                TaskRun.checkStop();
                LocalDate from = fromDate != null ? fromDate
                        : lastDate(c.getCode()).map(d -> d.minusDays(REVISION_DAYS)).orElse(HISTORY_FROM);
                if (from.isBefore(HISTORY_FROM)) {
                    from = HISTORY_FROM;
                }
                results.add(load(c.getCode(), from));
            }
            Result r = new Result(results);
            if (!r.changed().isEmpty()) {
                publisher.publish(CatalogChange.exchangeRates(r.changed()));
            }
            log.info("Cambio ({}) em {}s: {}", trigger, (System.currentTimeMillis() - started) / 1000, r);
            return r;
        } finally {
            running.unlock();
        }
    }

    private List<Currency> select(Collection<String> codes) {
        List<Currency> active = currencies.findByActiveTrueAndRateSourceOrderByCode(CurrencyRateSource.BCB_PTAX);
        if (codes == null || codes.isEmpty()) {
            return active;
        }
        Set<String> wanted = new TreeSet<>();
        codes.forEach(c -> wanted.add(c.trim().toUpperCase()));
        List<Currency> selected = active.stream().filter(c -> wanted.contains(c.getCode())).toList();
        if (selected.size() != wanted.size()) {
            Set<String> unknown = new TreeSet<>(wanted);
            selected.forEach(c -> unknown.remove(c.getCode()));
            throw new IllegalArgumentException("Moeda desconhecida, inativa ou sem fonte de câmbio: "
                    + String.join(", ", unknown));
        }
        return selected;
    }

    private Optional<LocalDate> lastDate(String code) {
        Date d = jdbc.queryForObject("SELECT MAX(reference_date) FROM exchange_rates WHERE currency_code = ?",
                Date.class, code);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    private CurrencyResult load(String code, LocalDate from) {
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int logged = 0;
        LocalDate changedFrom = null;
        String error = null;
        for (int year = from.getYear(); year <= today.getYear(); year++) {
            TaskRun.checkStop();
            List<PtaxClient.Bulletin> bulletins;
            try {
                bulletins = ptax.year(code, year);
            } catch (IndexSourceException e) {
                error = year + ": " + e.getMessage();
                log.error("Cambio: {} {} indisponivel: {}", code, year, e.getMessage());
                notices.sourceUnavailable(ROUTINE, SOURCE, code + " " + year + ": " + e.getMessage());
                continue;
            }
            LocalDate first = year == from.getYear() ? from : LocalDate.of(year, 1, 1);
            LocalDate last = LocalDate.of(year, 12, 31).isAfter(today) ? today : LocalDate.of(year, 12, 31);
            Map<LocalDate, BigDecimal[]> existing = new HashMap<>();
            jdbc.query("SELECT reference_date, buy_rate, sell_rate FROM exchange_rates "
                            + "WHERE currency_code = ? AND reference_date BETWEEN ? AND ?",
                    rs -> { existing.put(rs.getDate(1).toLocalDate(),
                            new BigDecimal[]{rs.getBigDecimal(2), rs.getBigDecimal(3)}); },
                    code, Date.valueOf(first), Date.valueOf(last));
            List<Object[]> inserts = new ArrayList<>();
            List<Object[]> updates = new ArrayList<>();
            for (PtaxClient.Bulletin b : bulletins) {
                if (b.date().isBefore(first) || b.date().isAfter(last)) {
                    continue;
                }
                BigDecimal buy = b.buy().setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
                BigDecimal sell = b.sell().setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
                BigDecimal[] old = existing.get(b.date());
                if (old == null) {
                    inserts.add(new Object[]{code, Date.valueOf(b.date()), buy, sell, Timestamp.valueOf(b.at())});
                } else if (old[0].compareTo(buy) != 0 || old[1].compareTo(sell) != 0) {
                    updates.add(new Object[]{buy, sell, Timestamp.valueOf(b.at()), code, Date.valueOf(b.date())});
                    if (logged++ < MAX_CHANGE_LOGS) {
                        log.warn("Cambio: {} em {} corrigido pela fonte: {}/{} -> {}/{}", code, b.date(),
                                old[0].stripTrailingZeros().toPlainString(), old[1].stripTrailingZeros().toPlainString(),
                                buy.stripTrailingZeros().toPlainString(), sell.stripTrailingZeros().toPlainString());
                    }
                } else {
                    unchanged++;
                    continue;
                }
                if (changedFrom == null || b.date().isBefore(changedFrom)) {
                    changedFrom = b.date();
                }
            }
            if (!inserts.isEmpty() || !updates.isEmpty()) {
                tx.executeWithoutResult(s -> {
                    if (!inserts.isEmpty()) {
                        jdbc.batchUpdate("INSERT INTO exchange_rates (currency_code, reference_date, buy_rate, sell_rate, "
                                + "bulletin_at, source) VALUES (?, ?, ?, ?, ?, 'BCB_PTAX') "
                                + "ON CONFLICT (currency_code, reference_date) DO NOTHING", inserts);
                    }
                    if (!updates.isEmpty()) {
                        jdbc.batchUpdate("UPDATE exchange_rates SET buy_rate = ?, sell_rate = ?, bulletin_at = ?, "
                                + "updated_at = now() WHERE currency_code = ? AND reference_date = ?", updates);
                    }
                });
            }
            created += inserts.size();
            updated += updates.size();
        }
        LocalDate lastDate = lastDate(code).orElse(null);
        LocalDate staleSince = error == null ? checkStale(code, lastDate, today) : null;
        return new CurrencyResult(code, from, created, updated, unchanged, changedFrom, lastDate, staleSince, error);
    }

    /** C1a: mais de 2 dias úteis sem boletim (entre o último e ontem, exclusive) → aviso; em dia → resolve. */
    private LocalDate checkStale(String code, LocalDate lastDate, LocalDate today) {
        if (lastDate == null) {
            return null;
        }
        int missing = lastDate.plusDays(1).isAfter(today.minusDays(1)) ? 0
                : BusinessCalendar.businessDaysBetween(lastDate.plusDays(1), today.minusDays(1));
        String source = SOURCE + " — " + code;
        if (missing > STALE_BUSINESS_DAYS) {
            log.warn("Cambio: a fonte respondeu, mas o boletim mais recente de {} e de {} ({} dias uteis sem publicacao)",
                    code, lastDate, missing);
            notices.sourceStale(ROUTINE, source, lastDate, missing);
            return lastDate;
        }
        notices.sourceCurrent(ROUTINE, source);
        return null;
    }
}
