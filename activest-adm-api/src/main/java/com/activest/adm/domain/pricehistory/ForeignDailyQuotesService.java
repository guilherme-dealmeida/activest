package com.activest.adm.domain.pricehistory;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.foreign.ForeignQuotesProvider;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.DayQuotes;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.Quote;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.Status;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.activest.domain.task.TaskRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Preço diário dos ativos internacionais vivos (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md): um pregão inteiro por
 * pedido à fonte ({@link ForeignQuotesProvider}, hoje o Massive grouped daily).
 *
 * <ul>
 *   <li>Os tipos com {@code price_source = MASSIVE} (Stock, REIT, ETF internacional): cada pregão casa com o papel
 *   <b>listado naquele dia</b> — o vivo ou, até a data de saída, o deslistado (desde a carga retroativa,
 *   docs/planos/PLANO_PRECOS_HISTORICOS_INTERNACIONAIS.md); depois da saída, o deslistado não recebe mais nada.</li>
 *   <li>Upsert em {@code asset_price_history} com {@code source = MASSIVE} (D2), em lote; dia regravado com o mesmo
 *   fechamento não muda.</li>
 *   <li>{@code assets.current_price} e {@code price_updated_at} só mudam com pregão mais novo que o do preço gravado
 *   (I2); {@code price_updated_at} = o pregão às 18:00 (fechamento de Nova York no horário de Brasília).</li>
 *   <li>Ativo vivo que não veio no dia mantém o último preço (D6); a lista vai para o log e a contagem para o resumo.</li>
 *   <li>Preço novo avisa a carteira ({@code CatalogChange.FECHAMENTO}, D10).</li>
 *   <li>Rodada sem período: do pregão seguinte ao último gravado até ontem em Nova York, no máximo
 *   {@value #MAX_DAYS} dias (D7). Dia não liberado ou sem pregão não é falha (I4, I5).</li>
 *   <li>Fonte recusou ou caiu: {@code FONTE_INDISPONIVEL}; respondeu, mas o último pregão gravado está mais de
 *   {@value #STALE_WEEKDAYS} dias úteis atrás: {@code FONTE_DESATUALIZADA}, resolvida quando o pregão chega (I3).</li>
 * </ul>
 */
@Slf4j
@Service
public class ForeignDailyQuotesService {

    /** Período máximo de uma rodada (D7, I6). */
    public static final int MAX_DAYS = 31;
    /** Dias úteis (segunda a sexta) sem pregão gravado que a fonte pode ficar sem aviso (I3, como o Tesouro). */
    static final int STALE_WEEKDAYS = 2;
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final String ROUTINE = "Preço dos ativos internacionais";
    private static final LocalTime CLOSE_TIME = LocalTime.of(18, 0);
    private static final int BATCH = 5_000;

    /**
     * Os vivos e os deslistados que ainda estavam na bolsa em algum dia do período (saída a partir do primeiro dia):
     * o pregão de cada dia casa com o papel listado nele (docs/planos/PLANO_PRECOS_HISTORICOS_INTERNACIONAIS.md).
     */
    private static final String CATALOG_ASSETS = """
            SELECT a.id, a.ticker, t.name AS type_name, a.delisted_on FROM assets a
            JOIN asset_types t ON t.id = a.asset_type_id
            WHERE t.price_source = 'MASSIVE'
              AND ((a.active AND a.delisted_on IS NULL) OR a.delisted_on >= ?)
            """;
    private static final String UPSERT = """
            INSERT INTO asset_price_history
                (asset_id, reference_date, open_price, high_price, low_price, close_price, volume, source,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'MASSIVE', NOW(), NOW())
            ON CONFLICT (asset_id, reference_date) DO UPDATE SET
                open_price = EXCLUDED.open_price, high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price, close_price = EXCLUDED.close_price,
                volume = EXCLUDED.volume, source = 'MASSIVE', updated_at = NOW()
            WHERE (asset_price_history.open_price, asset_price_history.high_price, asset_price_history.low_price,
                   asset_price_history.close_price, asset_price_history.volume, asset_price_history.source)
                  IS DISTINCT FROM
                  (EXCLUDED.open_price, EXCLUDED.high_price, EXCLUDED.low_price, EXCLUDED.close_price,
                   EXCLUDED.volume, EXCLUDED.source)
            """;
    /** Preço atual só com pregão igual ou mais novo que o gravado (I2), e só quando muda. */
    private static final String UPDATE_CURRENT = """
            UPDATE assets SET current_price = ?, price_updated_at = ?
            WHERE id = ? AND (price_updated_at IS NULL OR price_updated_at <= ?)
              AND (current_price IS DISTINCT FROM ? OR price_updated_at IS DISTINCT FROM ?)
            """;

    private final ForeignQuotesProvider provider;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired(required = false)
    CatalogChangePublisher catalogChangePublisher;

    @Autowired(required = false)
    AdminNotices notices;

    @Autowired
    public ForeignDailyQuotesService(ForeignQuotesProvider provider, JdbcTemplate jdbc) {
        this(provider, jdbc, Clock.systemDefaultZone());
    }

    ForeignDailyQuotesService(ForeignQuotesProvider provider, JdbcTemplate jdbc, Clock clock) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Um pregão da rodada. */
    public record DayResult(LocalDate date, Status status, boolean fromDisk, int matched, int written) {
    }

    /**
     * Resultado da rodada. A cobertura ({@code matched}, {@code missing*}) é a do pregão mais recente com cotações.
     *
     * @param staleSince último pregão gravado, quando a fonte está atrasada (I3); nulo em dia
     */
    public record Result(LocalDate from, LocalDate to, List<DayResult> days, long quotesWritten, int pricesUpdated,
                         int liveAssets, LocalDate coverageDate, int matched, int missingStock, int missingReit,
                         int missingEtf, String coverage, LocalDate staleSince, int daysOutOfPlan) {
    }

    /** Rodada sem período: do pregão seguinte ao último gravado até ontem em Nova York (D7). */
    public Result importRecent(String reason) {
        LocalDate yesterday = lastWeekday(todayNewYork().minusDays(1));
        LocalDate last = lastDay();
        LocalDate from = last == null ? yesterday : last.plusDays(1);
        if (from.isBefore(yesterday.minusDays(MAX_DAYS - 1L))) {
            from = yesterday.minusDays(MAX_DAYS - 1L);
        }
        if (from.isAfter(yesterday)) {
            log.info("Preço dos internacionais ({}): em dia (último pregão {})", reason, last);
            return finish(from, yesterday, List.of(), 0, 0, new Coverage(0), 0, reason);
        }
        return importDays(from, yesterday, reason);
    }

    /**
     * Carga retroativa (tarefa {@code FOREIGN_HISTORY_QUOTES}): como {@link #importDays}, mas o dia que a fonte não
     * entrega no plano (fora dos 2 anos do gratuito) é contado e pulado, sem interromper a rodada.
     */
    public Result importHistory(LocalDate from, LocalDate to, String reason) {
        return importDays(from, to, reason, true);
    }

    /**
     * Pregões de {@code from} a {@code to}, inclusive (sábado e domingo não vão à fonte).
     *
     * @return nulo quando já há uma rodada em andamento
     * @throws ForeignQuotesProvider.OutOfRangeException dia fora do plano da fonte
     */
    public Result importDays(LocalDate from, LocalDate to, String reason) {
        return importDays(from, to, reason, false);
    }

    private Result importDays(LocalDate from, LocalDate to, String reason, boolean history) {
        if (!running.tryLock()) {
            log.warn("Preço dos internacionais ({}): já existe uma rodada em andamento; nada feito", reason);
            return null;
        }
        try {
            Map<String, List<LiveAsset>> catalog = catalog(from);
            List<DayResult> days = new ArrayList<>();
            long written = 0;
            int updated = 0;
            int outOfPlan = 0;
            Set<Long> changed = new HashSet<>();
            Coverage coverage = new Coverage(0);
            // o que já foi gravado avisa a carteira mesmo se a rodada parar no meio (Stop, fonte fora do ar)
            try {
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                    if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        continue;
                    }
                    TaskRun.checkStop(); // ponto de parada entre pregões
                    DayQuotes q;
                    try {
                        q = provider.day(d);
                    } catch (ForeignQuotesProvider.OutOfRangeException e) {
                        if (!history) {
                            throw e;
                        }
                        outOfPlan++;
                        log.info("Preço dos internacionais ({}): {} — fora do plano da fonte", reason, d);
                        continue;
                    } catch (ForeignQuotesProvider.AuthException | ForeignQuotesProvider.UnavailableException e) {
                        if (notices != null) {
                            notices.sourceUnavailable(ROUTINE, provider.sourceName(), e.getMessage());
                        }
                        throw e;
                    }
                    if (q.status() != Status.OK) {
                        log.info("Preço dos internacionais ({}): {} — {}", reason, d,
                                q.status() == Status.NOT_RELEASED ? "ainda não liberado pela fonte" : "sem pregão");
                        days.add(new DayResult(d, q.status(), false, 0, 0));
                        continue;
                    }
                    Map<String, LiveAsset> byTicker = listedOn(catalog, d);
                    DayWrite w = write(d, q.quotes(), byTicker, changed);
                    written += w.written();
                    updated += w.updated();
                    coverage = w.coverage();
                    days.add(new DayResult(d, Status.OK, q.fromDisk(), w.coverage().matched, w.written()));
                    log.info("Preço dos internacionais ({}): {} — {} papéis na fonte{}, {} de {} listados casados, {} "
                                    + "fechamentos gravados, {} preços atuais", reason, d, q.quotes().size(),
                            q.fromDisk() ? " (arquivo em disco)" : "", w.coverage().matched, byTicker.size(),
                            w.written(), w.updated());
                }
            } finally {
                publish(changed);
            }
            // a lista dos sem cotação só do pregão mais recente (na carga retroativa seriam centenas de listas)
            if (!coverage.missing.isEmpty()) {
                log.info("Preço dos internacionais: {} sem cotação em {}: {}", coverage.missing.size(), coverage.date,
                        String.join(" ", coverage.missing));
            }
            return finish(from, to, days, written, updated, coverage, outOfPlan, reason);
        } finally {
            running.unlock();
        }
    }

    /** Último pregão gravado pela fonte; nulo sem nenhum. */
    public LocalDate lastDay() {
        return jdbc.queryForObject(
                "SELECT MAX(reference_date) FROM asset_price_history WHERE source = 'MASSIVE'", LocalDate.class);
    }

    /** Atrasado quando falta o pregão do último dia útil antes de hoje em Nova York (recuperação na subida). */
    public boolean stale() {
        LocalDate last = lastDay();
        return last == null || last.isBefore(lastWeekday(todayNewYork().minusDays(1)));
    }

    // ------------------------------------------------------------------ gravação

    private record LiveAsset(long id, String ticker, String type, LocalDate delistedOn) {
    }

    private record DayWrite(int written, int updated, Coverage coverage) {
    }

    /** Casados e faltantes do pregão, por tipo. */
    private static final class Coverage {
        final int live;
        int matched;
        final Map<String, Integer> missingByType = new TreeMap<>();
        final List<String> missing = new ArrayList<>();
        LocalDate date;

        Coverage(int live) {
            this.live = live;
        }
    }

    /** Candidatos por ticker: o vivo e os deslistados com saída a partir de {@code from}. */
    private Map<String, List<LiveAsset>> catalog(LocalDate from) {
        Map<String, List<LiveAsset>> out = new HashMap<>();
        jdbc.query(CATALOG_ASSETS, rs -> {
            Date delisted = rs.getDate("delisted_on");
            LiveAsset a = new LiveAsset(rs.getLong("id"), rs.getString("ticker"), rs.getString("type_name"),
                    delisted == null ? null : delisted.toLocalDate());
            out.computeIfAbsent(a.ticker().toUpperCase(), k -> new ArrayList<>()).add(a);
        }, Date.valueOf(from));
        return out;
    }

    /**
     * O papel listado em {@code d} para cada ticker (ticker reaproveitado, V13): o deslistado de saída mais próxima
     * ainda não passada ({@code delisted_on >= d}); sem ele, o vivo.
     */
    static Map<String, LiveAsset> listedOn(Map<String, List<LiveAsset>> catalog, LocalDate d) {
        Map<String, LiveAsset> out = new HashMap<>(catalog.size() * 2);
        catalog.forEach((ticker, candidates) -> {
            LiveAsset pick = null;
            for (LiveAsset a : candidates) {
                if (a.delistedOn() == null) {
                    if (pick == null) {
                        pick = a;
                    }
                } else if (!a.delistedOn().isBefore(d)
                        && (pick == null || pick.delistedOn() == null || a.delistedOn().isBefore(pick.delistedOn()))) {
                    pick = a;
                }
            }
            if (pick != null) {
                out.put(ticker, pick);
            }
        });
        return out;
    }

    private DayWrite write(LocalDate d, List<Quote> quotes, Map<String, LiveAsset> byTicker, Set<Long> changed) {
        Coverage cov = new Coverage(byTicker.size());
        cov.date = d;
        Set<Long> seen = new HashSet<>();
        List<Object[]> history = new ArrayList<>(BATCH);
        List<Object[]> current = new ArrayList<>();
        List<Long> currentIds = new ArrayList<>();
        int written = 0;
        Date day = Date.valueOf(d);
        Timestamp at = Timestamp.valueOf(d.atTime(CLOSE_TIME));
        for (Quote q : quotes) {
            LiveAsset a = byTicker.get(q.ticker().toUpperCase());
            if (a == null || q.close().signum() <= 0 || !seen.add(a.id())) {
                continue;
            }
            history.add(new Object[]{a.id(), day, q.open(), q.high(), q.low(), q.close(), q.volume()});
            current.add(new Object[]{q.close(), at, a.id(), at, q.close(), at});
            currentIds.add(a.id());
            if (history.size() >= BATCH) {
                written += sum(jdbc.batchUpdate(UPSERT, history));
                history.clear();
            }
        }
        written += sum(jdbc.batchUpdate(UPSERT, history));
        int updated = 0;
        int[] counts = jdbc.batchUpdate(UPDATE_CURRENT, current);
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                updated++;
                changed.add(currentIds.get(i));
            }
        }
        cov.matched = seen.size();
        for (LiveAsset a : byTicker.values()) {
            if (!seen.contains(a.id())) {
                cov.missingByType.merge(a.type(), 1, Integer::sum);
                cov.missing.add(a.ticker());
            }
        }
        cov.missing.sort(null);
        return new DayWrite(written, updated, cov);
    }

    private static int sum(int[] counts) {
        int n = 0;
        for (int c : counts) {
            n += Math.max(c, 0);
        }
        return n;
    }

    private void publish(Set<Long> changed) {
        if (changed.isEmpty() || catalogChangePublisher == null) {
            return;
        }
        try {
            catalogChangePublisher.publish(new CatalogChange(CatalogChange.Origin.FECHAMENTO, Set.copyOf(changed)));
        } catch (RuntimeException e) {
            log.warn("Preço dos internacionais: aviso à carteira não publicado: {}", e.getMessage());
        }
        changed.clear();
    }

    // ------------------------------------------------------------------ conclusão

    private Result finish(LocalDate from, LocalDate to, List<DayResult> days, long written, int updated,
                          Coverage cov, int outOfPlan, String reason) {
        LocalDate staleSince = staleness();
        String coverage = cov.date == null || cov.live == 0 ? "-"
                : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * cov.matched / cov.live);
        Result r = new Result(from, to, days, written, updated, cov.live, cov.date, cov.matched,
                cov.missingByType.getOrDefault("STOCK", 0), cov.missingByType.getOrDefault("REIT", 0),
                cov.missingByType.getOrDefault("ETF_INTERNACIONAL", 0), coverage, staleSince, outOfPlan);
        log.info("Preço dos internacionais ({}) concluído: {} a {}, {} fechamentos, {} preços atuais, cobertura {}",
                reason, from, to, written, updated, coverage);
        return r;
    }

    /** A fonte respondeu: o último pregão gravado está em dia? (I3) */
    private LocalDate staleness() {
        LocalDate last = lastDay();
        if (last == null || notices == null) {
            return null;
        }
        LocalDate yesterday = lastWeekday(todayNewYork().minusDays(1));
        int missing = 0;
        for (LocalDate d = last.plusDays(1); !d.isAfter(yesterday); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                missing++;
            }
        }
        if (missing > STALE_WEEKDAYS) {
            notices.sourceStale(ROUTINE, provider.sourceName(), last, missing);
            return last;
        }
        notices.sourceCurrent(ROUTINE, provider.sourceName());
        return null;
    }

    private LocalDate todayNewYork() {
        return LocalDate.now(clock.withZone(NEW_YORK));
    }

    static LocalDate lastWeekday(LocalDate d) {
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }
}
