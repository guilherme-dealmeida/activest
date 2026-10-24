package com.activest.adm.domain.marketindex;

import com.activest.domain.task.TaskRun;

import com.activest.adm.integration.indices.B3IndexClient;
import com.activest.adm.integration.indices.BcbSgsClient;
import com.activest.adm.integration.indices.IndexSourceException;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.activest.domain.marketindex.IndexPeriodicity;
import com.activest.domain.marketindex.IndexPoint;
import com.activest.domain.marketindex.MarketIndex;
import com.activest.domain.marketindex.MarketIndexRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Series dos indices de mercado em {@code market_index_values} (docs/planos/PLANO_INDICES_MERCADO.md, secao 4).
 * Catalogo puro: so a adm-api grava; a app-api le.
 *
 * <ul>
 *   <li><b>Carga historica</b> ({@link #importYears}, {@link #importAll}): por indice e por ano, do
 *   {@code history_from} (2010, D2) ate o ano corrente. Ano fechado com arquivo em {@code carga/data} nao vai a
 *   fonte (D3).</li>
 *   <li><b>Incremental</b> ({@link #importRecent}, rotina das 10:00 e subida): de (ultima data gravada − janela de
 *   revisao) ate hoje — 30 dias nos diarios, 3 meses nos mensais (D10). Indice sem nada gravado faz a carga
 *   historica inteira.</li>
 * </ul>
 *
 * <p>So entra data ate hoje: a meta Selic do SGS vem preenchida dia a dia ate a proxima reuniao do Copom, e um
 * "ultimo valor" no futuro confundiria a conferencia e a tela.</p>
 *
 * <p><b>Idempotente.</b> Valor igual e pulado; valor que a fonte corrigiu e sobrescrito, com log
 * {@code antigo → novo} (D10). Nada e apagado. Indice que falha nao impede os outros. Uma carga por vez.</p>
 *
 * <p>O resultado traz, por indice, a menor data que mudou; quando algo mudou, publica "catalogo mudou" (INDICES) e a
 * app-api refaz a curva da renda fixa privada (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R5).</p>
 */
@Slf4j
@Service
public class MarketIndexImportService {

    /** Mensagens de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.activest.adm.domain.notice.AdminNotices notices;

    static final int DAILY_REVISION_DAYS = 30;
    static final int MONTHLY_REVISION_MONTHS = 3;
    /** Diario sem valor ha mais que isto (~5 dias uteis) esta atrasado. */
    static final int DAILY_STALE_DAYS = 7;
    /** Mensal atrasado: passou disto depois do fim do mes de referencia sem o mes seguinte. */
    static final int MONTHLY_STALE_DAYS = 45;
    private static final int MAX_CHANGE_LOGS = 20;
    private static final int VALUE_SCALE = 8;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final MarketIndexRepository indices;
    private final BcbSgsClient sgs;
    private final B3IndexClient b3;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CatalogChangePublisher publisher;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired
    public MarketIndexImportService(MarketIndexRepository indices, BcbSgsClient sgs, B3IndexClient b3,
                                    JdbcTemplate jdbc, PlatformTransactionManager txm, CatalogChangePublisher publisher) {
        this(indices, sgs, b3, jdbc, txm, publisher, Clock.system(ZONE));
    }

    MarketIndexImportService(MarketIndexRepository indices, BcbSgsClient sgs, B3IndexClient b3, JdbcTemplate jdbc,
                             PlatformTransactionManager txm, CatalogChangePublisher publisher, Clock clock) {
        this.indices = indices;
        this.sgs = sgs;
        this.b3 = b3;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Um indice na rodada. {@code error} nulo = ok; {@code lastDate} = ultima data gravada depois da rodada. */
    public record IndexResult(String code, int fromYear, int toYear, int created, int updated, int unchanged,
                              LocalDate changedFrom, LocalDate lastDate, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public record Result(List<IndexResult> indices) {

        public int created() {
            return indices.stream().mapToInt(IndexResult::created).sum();
        }

        public int updated() {
            return indices.stream().mapToInt(IndexResult::updated).sum();
        }

        public List<String> failures() {
            return indices.stream().filter(r -> !r.ok()).map(IndexResult::code).toList();
        }

        /** Por indice, a menor data que entrou ou mudou (D8: base da revalorizacao futura). */
        public Map<String, LocalDate> changedFrom() {
            Map<String, LocalDate> m = new LinkedHashMap<>();
            indices.stream().filter(r -> r.changedFrom() != null).forEach(r -> m.put(r.code(), r.changedFrom()));
            return m;
        }

        @Override
        public String toString() {
            return indices.size() + " indices, " + created() + " novos, " + updated() + " corrigidos"
                    + (failures().isEmpty() ? "" : ", falharam: " + String.join(", ", failures()));
        }
    }

    /** Indice atrasado: sem valor ({@code lastDate} nulo) ou sem o periodo seguinte quando ja devia ter. */
    public record Stale(String code, LocalDate lastDate) {
    }

    /** Carga historica de todos os indices ativos: de {@code history_from} ate o ano corrente. */
    public Result importAll(String trigger) {
        return importYears(null, null, LocalDate.now(clock).getYear(), trigger);
    }

    /**
     * Carga por ano.
     *
     * @param codes    indices a carregar; nulo ou vazio = todos os ativos
     * @param fromYear primeiro ano; nulo = o ano de {@code history_from} de cada indice
     * @return nulo se ja ha uma carga rodando
     * @throws IllegalArgumentException codigo desconhecido
     */
    public Result importYears(Collection<String> codes, Integer fromYear, int toYear, String trigger) {
        List<MarketIndex> selected = select(codes);
        if (!running.tryLock()) {
            log.warn("Indices ({}): ja existe uma carga rodando; nada feito", trigger);
            return null;
        }
        try {
            long started = System.currentTimeMillis();
            List<IndexResult> results = new ArrayList<>();
            for (MarketIndex index : selected) {
                TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
                int from = fromYear != null ? fromYear : index.getHistoryFrom().getYear();
                results.add(load(index, from, toYear));
            }
            return finish(trigger, results, started);
        } finally {
            running.unlock();
        }
    }

    /** Rodada incremental (rotina diaria e recuperacao na subida). Nulo se ja ha uma carga rodando. */
    public Result importRecent(String trigger) {
        List<MarketIndex> active = indices.findByActiveTrueOrderByIdAsc();
        if (!running.tryLock()) {
            log.warn("Indices ({}): ja existe uma carga rodando; nada feito", trigger);
            return null;
        }
        try {
            long started = System.currentTimeMillis();
            int year = LocalDate.now(clock).getYear();
            List<IndexResult> results = new ArrayList<>();
            for (MarketIndex index : active) {
                TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
                Optional<LocalDate> last = lastDate(index.getId());
                int from = last.map(d -> revisionStart(index, d).getYear())
                        .orElse(index.getHistoryFrom().getYear());
                results.add(load(index, Math.min(from, year), year));
            }
            return finish(trigger, results, started);
        } finally {
            running.unlock();
        }
    }

    /** Indices ativos atrasados, pela regra da conferencia (4.2): diario > 7 dias, mensal > 45 dias do fim do mes. */
    public List<Stale> stale() {
        LocalDate today = LocalDate.now(clock);
        List<Stale> out = new ArrayList<>();
        for (MarketIndex index : indices.findByActiveTrueOrderByIdAsc()) {
            Optional<LocalDate> last = lastDate(index.getId());
            boolean late = last.map(d -> index.getPeriodicity() == IndexPeriodicity.DIARIA
                            ? d.isBefore(today.minusDays(DAILY_STALE_DAYS))
                            : d.plusMonths(1).plusDays(MONTHLY_STALE_DAYS).isBefore(today))
                    .orElse(true);
            if (late) {
                out.add(new Stale(index.getCode(), last.orElse(null)));
            }
        }
        return out;
    }

    /** Conferencia semanal: log e mensagem de administrador por indice atrasado; o que voltou ao dia fica resolvido. */
    public List<Stale> audit(String reason) {
        List<Stale> late = stale();
        late.forEach(s -> log.warn("Indices: {} atrasado — ultimo valor {}", s.code(),
                s.lastDate() == null ? "nenhum (carga historica nao rodou)" : s.lastDate()));
        late.forEach(s -> notices.pending(com.activest.domain.notification.NotificationKind.CATALOGO_INDICE_ATRASADO,
                String.valueOf(s.code()), "Índice " + s.code() + " atrasado",
                s.lastDate() == null
                        ? "O índice não tem série: a carga histórica não rodou (POST /api/v1/admin/indices)."
                        : "O último valor gravado é de " + s.lastDate().format(java.time.format.DateTimeFormatter
                                .ofPattern("dd/MM/yyyy")) + ". A fonte pode estar atrasada ou a rotina parada.",
                List.of()));
        notices.resolveAllExcept(com.activest.domain.notification.NotificationKind.CATALOGO_INDICE_ATRASADO,
                late.stream().map(s -> String.valueOf(s.code())).toList());
        log.info("Conferencia dos indices ({}): {} atrasado(s)", reason, late.size());
        return late;
    }

    static LocalDate revisionStart(MarketIndex index, LocalDate last) {
        return index.getPeriodicity() == IndexPeriodicity.DIARIA
                ? last.minusDays(DAILY_REVISION_DAYS)
                : last.minusMonths(MONTHLY_REVISION_MONTHS);
    }

    /** Confere os codigos antes de disparar uma carga em segundo plano. */
    public void validateCodes(Collection<String> codes) {
        select(codes);
    }

    private List<MarketIndex> select(Collection<String> codes) {
        List<MarketIndex> active = indices.findByActiveTrueOrderByIdAsc();
        if (codes == null || codes.isEmpty()) {
            return active;
        }
        Set<String> wanted = new TreeSet<>();
        codes.forEach(c -> wanted.add(c.trim().toUpperCase()));
        List<MarketIndex> selected = active.stream().filter(i -> wanted.contains(i.getCode())).toList();
        if (selected.size() != wanted.size()) {
            Set<String> unknown = new TreeSet<>(wanted);
            selected.forEach(i -> unknown.remove(i.getCode()));
            throw new IllegalArgumentException("Indice desconhecido ou inativo: " + String.join(", ", unknown));
        }
        return selected;
    }

    private Optional<LocalDate> lastDate(long indexId) {
        Date d = jdbc.queryForObject("SELECT MAX(reference_date) FROM market_index_values WHERE index_id = ?",
                Date.class, indexId);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    private Result finish(String trigger, List<IndexResult> results, long started) {
        Result r = new Result(results);
        // D8 da renda fixa (R5): a app-api revaloriza a renda fixa privada quando um indice entra ou muda
        if (!r.changedFrom().isEmpty()) {
            publisher.publish(CatalogChange.indices(r.changedFrom().keySet()));
        }
        log.info("Indices ({}) em {}s: {}", trigger, (System.currentTimeMillis() - started) / 1000, r);
        results.forEach(i -> log.info("Indices: {} {}-{} — {} novos, {} corrigidos, {} iguais, ultimo {}{}",
                i.code(), i.fromYear(), i.toYear(), i.created(), i.updated(), i.unchanged(), i.lastDate(),
                i.ok() ? "" : " — FALHOU: " + i.error()));
        return r;
    }

    private IndexResult load(MarketIndex index, int fromYear, int toYear) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        LocalDate changedFrom = null;
        String error = null;
        int logged = 0;
        for (int year = fromYear; year <= toYear; year++) {
            TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
            List<IndexPoint> points;
            try {
                points = fetch(index, year);
            } catch (IndexSourceException e) {
                error = year + ": " + e.getMessage();
                log.error("Indices: {} {} indisponivel: {}", index.getCode(), year, e.getMessage());
                notices.sourceUnavailable("Índices de mercado",
                        index.getSource() == com.activest.domain.marketindex.IndexSource.B3 ? "B3" : "Banco Central (SGS)",
                        index.getCode()
                        + " " + year + ": " + e.getMessage());
                continue;
            }
            LocalDate first = LocalDate.of(year, 1, 1);
            // nada com data futura: a meta Selic (SGS 432) vem preenchida ate a proxima reuniao do Copom
            LocalDate today = LocalDate.now(clock);
            LocalDate last = LocalDate.of(year, 12, 31).isAfter(today) ? today : LocalDate.of(year, 12, 31);
            Map<LocalDate, BigDecimal> existing = new HashMap<>();
            jdbc.query("SELECT reference_date, value FROM market_index_values "
                            + "WHERE index_id = ? AND reference_date BETWEEN ? AND ?",
                    rs -> { existing.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)); },
                    index.getId(), Date.valueOf(first), Date.valueOf(last));
            List<Object[]> inserts = new ArrayList<>();
            List<Object[]> updates = new ArrayList<>();
            for (IndexPoint p : points) {
                if (p.date().isBefore(first) || p.date().isAfter(last)) {
                    continue;
                }
                // a coluna e numeric(18,8): comparar ja na escala gravada, senao todo valor com mais casas
                // pareceria "corrigido" a cada rodada
                BigDecimal value = p.value().setScale(VALUE_SCALE, RoundingMode.HALF_EVEN);
                BigDecimal old = existing.get(p.date());
                if (old == null) {
                    inserts.add(new Object[]{index.getId(), Date.valueOf(p.date()), value});
                } else if (old.compareTo(value) != 0) {
                    updates.add(new Object[]{value, index.getId(), Date.valueOf(p.date())});
                    if (logged++ < MAX_CHANGE_LOGS) {
                        log.warn("Indices: {} em {} corrigido pela fonte: {} -> {}", index.getCode(), p.date(),
                                old.stripTrailingZeros().toPlainString(), value.stripTrailingZeros().toPlainString());
                    }
                } else {
                    unchanged++;
                    continue;
                }
                if (changedFrom == null || p.date().isBefore(changedFrom)) {
                    changedFrom = p.date();
                }
            }
            if (!inserts.isEmpty() || !updates.isEmpty()) {
                tx.executeWithoutResult(s -> {
                    if (!inserts.isEmpty()) {
                        jdbc.batchUpdate("INSERT INTO market_index_values (index_id, reference_date, value) "
                                + "VALUES (?, ?, ?) ON CONFLICT (index_id, reference_date) DO NOTHING", inserts);
                    }
                    if (!updates.isEmpty()) {
                        jdbc.batchUpdate("UPDATE market_index_values SET value = ?, updated_at = now() "
                                + "WHERE index_id = ? AND reference_date = ?", updates);
                    }
                });
            }
            created += inserts.size();
            updated += updates.size();
        }
        return new IndexResult(index.getCode(), fromYear, toYear, created, updated, unchanged, changedFrom,
                lastDate(index.getId()).orElse(null), error);
    }

    private List<IndexPoint> fetch(MarketIndex index, int year) {
        return switch (index.getSource()) {
            case BCB_SGS -> sgs.year(index.getSourceRef(), year);
            case B3 -> b3.year(index.getSourceRef(), year);
        };
    }
}
