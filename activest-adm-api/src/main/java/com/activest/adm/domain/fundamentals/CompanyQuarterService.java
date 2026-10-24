package com.activest.adm.domain.fundamentals;

import com.activest.adm.domain.fundamentals.QuarterSeries.Doc;
import com.activest.adm.domain.fundamentals.QuarterSeries.Flow;
import com.activest.adm.domain.fundamentals.QuarterSeries.Quarter;
import com.activest.adm.domain.fundamentals.QuarterSeries.Shares;
import com.activest.adm.domain.fundamentals.QuarterSeries.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code company_quarters} (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F6): serie trimestral de todas as companhias,
 * recalculada inteira a partir de {@code cvm_statement_values} e {@code share_counts} pelas regras de
 * {@link QuarterSeries}. Roda ao fim de cada carga de balancos e sob demanda; leva segundos.
 */
@Slf4j
@Service
public class CompanyQuarterService {

    public record Result(int issuers, int quarters, int withTtm, int withShares, long millis) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ReentrantLock running = new ReentrantLock();

    public CompanyQuarterService(JdbcTemplate jdbc, PlatformTransactionManager txm) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
    }

    private record Row(long issuerId, Quarter q) {
    }

    /** Uma companhia sendo lida: documentos na ordem do SELECT. */
    private static final class Pending {
        final Map<Long, DocBuilder> docs = new LinkedHashMap<>();
    }

    private static final class DocBuilder {
        final long id;
        final String docType;
        final LocalDate referenceDate;
        final Shares shares;
        final List<Value> values = new ArrayList<>();

        DocBuilder(ResultSet rs) throws SQLException {
            id = rs.getLong("document_id");
            docType = rs.getString("doc_type");
            referenceDate = rs.getDate("reference_date").toLocalDate();
            long factor = rs.getLong("scale_factor");
            if (rs.wasNull()) {
                shares = Shares.NONE;
            } else {
                long total = rs.getLong("total_shares") * factor;
                long treasury = rs.getLong("total_treasury") * factor; // nulo -> 0
                shares = new Shares(total, total - treasury);
            }
        }

        Doc build() {
            return new Doc(id, docType, referenceDate, values, shares);
        }
    }

    public Result rebuild(String trigger) {
        running.lock();
        try {
            long started = System.currentTimeMillis();
            List<Row> rows = new ArrayList<>();
            long[] current = {-1};
            Pending[] pending = {new Pending()};
            jdbc.query("""
                    SELECT d.issuer_id, d.id AS document_id, d.doc_type, d.reference_date,
                           s.total_shares, s.total_treasury, s.scale_factor,
                           v.metric, v.period_start, v.period_end, v.value
                    FROM cvm_documents d
                    LEFT JOIN share_counts s ON s.document_id = d.id
                    LEFT JOIN cvm_statement_values v ON v.document_id = d.id
                    ORDER BY d.issuer_id, d.reference_date, d.id""", rs -> {
                long issuerId = rs.getLong("issuer_id");
                if (issuerId != current[0]) {
                    flush(current[0], pending[0], rows);
                    current[0] = issuerId;
                    pending[0] = new Pending();
                }
                long docId = rs.getLong("document_id");
                DocBuilder b = pending[0].docs.get(docId);
                if (b == null) {
                    b = new DocBuilder(rs);
                    pending[0].docs.put(docId, b);
                }
                String metric = rs.getString("metric");
                if (metric != null) {
                    Date start = rs.getDate("period_start");
                    b.values.add(new Value(FinancialMetric.valueOf(metric),
                            start == null ? null : start.toLocalDate(),
                            rs.getDate("period_end").toLocalDate(), rs.getBigDecimal("value")));
                }
            });
            flush(current[0], pending[0], rows);

            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM company_quarters");
                jdbc.batchUpdate("""
                        INSERT INTO company_quarters (issuer_id, period_end, fiscal_year_start, source_doc_id,
                            revenue_q, ebit_q, net_income_q, net_income_parent_q, fco_q, dividends_declared_q,
                            dividends_paid_q,
                            revenue_ttm, ebit_ttm, net_income_ttm, net_income_parent_ttm, fco_ttm,
                            dividends_declared_ttm, dividends_paid_ttm, equity, equity_parent, total_assets, cash,
                            gross_debt, shares_total, shares_outstanding)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        rows, 1000, (ps, r) -> {
                            Quarter q = r.q();
                            int i = 1;
                            ps.setLong(i++, r.issuerId());
                            ps.setDate(i++, Date.valueOf(q.periodEnd()));
                            ps.setDate(i++, q.fiscalYearStart() == null ? null : Date.valueOf(q.fiscalYearStart()));
                            ps.setLong(i++, q.sourceDocId());
                            for (Flow f : Flow.values()) {
                                ps.setBigDecimal(i++, money(q.quarter().get(f)));
                            }
                            for (Flow f : Flow.values()) {
                                ps.setBigDecimal(i++, money(q.ttm().get(f)));
                            }
                            ps.setBigDecimal(i++, money(q.equity()));
                            ps.setBigDecimal(i++, money(q.equityParent()));
                            ps.setBigDecimal(i++, money(q.totalAssets()));
                            ps.setBigDecimal(i++, money(q.cash()));
                            ps.setBigDecimal(i++, money(q.grossDebt()));
                            ps.setObject(i++, q.sharesTotal(), Types.BIGINT);
                            ps.setObject(i, q.sharesOutstanding(), Types.BIGINT);
                        });
            });

            int issuers = (int) rows.stream().mapToLong(Row::issuerId).distinct().count();
            int withTtm = (int) rows.stream().filter(r -> r.q().ttm().containsKey(Flow.NET_INCOME_PARENT)).count();
            int withShares = (int) rows.stream().filter(r -> r.q().sharesOutstanding() != null).count();
            Result result = new Result(issuers, rows.size(), withTtm, withShares, System.currentTimeMillis() - started);
            log.info("Serie trimestral ({}): {}", trigger, result);
            return result;
        } finally {
            running.unlock();
        }
    }

    private static void flush(long issuerId, Pending p, List<Row> rows) {
        if (issuerId < 0 || p.docs.isEmpty()) {
            return;
        }
        List<Doc> docs = p.docs.values().stream().map(DocBuilder::build).toList();
        for (Quarter q : QuarterSeries.compute(docs)) {
            rows.add(new Row(issuerId, q));
        }
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
