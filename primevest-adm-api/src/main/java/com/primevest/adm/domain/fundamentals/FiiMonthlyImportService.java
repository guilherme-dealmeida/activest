package com.primevest.adm.domain.fundamentals;

import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmException;
import com.primevest.adm.integration.cvm.CvmFiiMonthly;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Informe mensal de FII da CVM em {@code fii_monthly} (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F7).
 *
 * <p>So os fundos que sao emissores ({@code issuers.kind = 'FII'}), ligados pelo CNPJ;
 * o resto do arquivo e descartado na leitura. Catalogo puro: nao mexe em carteira e
 * nao alimenta proventos (o rendimento estimado do informe e so indicador — 1.2).</p>
 *
 * <p><b>Idempotente.</b> Mes ja gravado com versao igual ou maior e pulado; versao nova
 * (reapresentacao) substitui a linha. {@code force} regrava tudo a partir de
 * {@code carga/data/cvm/fii}, sem baixar de novo.</p>
 */
@Slf4j
@Service
public class FiiMonthlyImportService {

    /** Os dados abertos do informe mensal comecam em 2016. */
    public static final int FIRST_YEAR = 2016;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final String UPSERT = """
            INSERT INTO fii_monthly (issuer_id, reference_month, version, net_assets, total_assets, shares_issued,
                nav_per_share, shareholders, admin_fee_month, dividend_yield_month, amortization_month,
                liquidity_total, total_invested, real_estate_total, real_estate_companies, receivables_total,
                fii_shares_total, liabilities_total, income_to_distribute, imported_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (issuer_id, reference_month) DO UPDATE SET
                version = EXCLUDED.version, net_assets = EXCLUDED.net_assets, total_assets = EXCLUDED.total_assets,
                shares_issued = EXCLUDED.shares_issued, nav_per_share = EXCLUDED.nav_per_share,
                shareholders = EXCLUDED.shareholders, admin_fee_month = EXCLUDED.admin_fee_month,
                dividend_yield_month = EXCLUDED.dividend_yield_month,
                amortization_month = EXCLUDED.amortization_month, liquidity_total = EXCLUDED.liquidity_total,
                total_invested = EXCLUDED.total_invested, real_estate_total = EXCLUDED.real_estate_total,
                real_estate_companies = EXCLUDED.real_estate_companies,
                receivables_total = EXCLUDED.receivables_total, fii_shares_total = EXCLUDED.fii_shares_total,
                liabilities_total = EXCLUDED.liabilities_total,
                income_to_distribute = EXCLUDED.income_to_distribute, imported_at = now()""";

    private final CvmOpenDataClient cvm;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired
    public FiiMonthlyImportService(CvmOpenDataClient cvm, JdbcTemplate jdbc, PlatformTransactionManager txm) {
        this(cvm, jdbc, txm, Clock.system(ZONE));
    }

    FiiMonthlyImportService(CvmOpenDataClient cvm, JdbcTemplate jdbc, PlatformTransactionManager txm, Clock clock) {
        this.cvm = cvm;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
    }

    /**
     * {@code months}: meses dos emissores lidos nos arquivos; {@code funds}: emissores com
     * ao menos um mes lido; {@code withoutReport}: emissores FII sem nenhum mes no periodo.
     */
    public record Result(int months, int created, int updated, int unchanged, int funds, int withoutReport) {
    }

    /** Ano corrente e anterior — o que a CVM ainda recebe e reapresenta (rotina diaria). */
    public Result importRecent(String trigger) {
        int year = LocalDate.now(clock).getYear();
        return importYears(year - 1, year, false, trigger);
    }

    public Result importYears(int from, int to, boolean force, String trigger) {
        if (!running.tryLock()) {
            log.warn("Informe FII ({}): ja existe uma carga rodando; nada feito", trigger);
            return null;
        }
        try {
            return run(Math.max(from, FIRST_YEAR), to, force, trigger);
        } finally {
            running.unlock();
        }
    }

    private record Existing(long issuerId, LocalDate month) {
    }

    private Result run(int from, int to, boolean force, String trigger) {
        long started = System.currentTimeMillis();
        log.info("Informe FII ({}): {} a {}{}", trigger, from, to, force ? ", regravando tudo" : "");
        Map<String, Long> issuers = new HashMap<>();
        jdbc.query("SELECT id, cnpj FROM issuers WHERE kind = 'FII' AND cnpj IS NOT NULL",
                rs -> { issuers.put(rs.getString("cnpj"), rs.getLong("id")); });
        Map<Existing, Integer> versions = new HashMap<>();
        jdbc.query("SELECT issuer_id, reference_month, version FROM fii_monthly WHERE reference_month BETWEEN ? AND ?",
                rs -> {
                    versions.put(new Existing(rs.getLong("issuer_id"), rs.getDate("reference_month").toLocalDate()),
                            rs.getInt("version"));
                },
                Date.valueOf(LocalDate.of(from, 1, 1)), Date.valueOf(LocalDate.of(to, 12, 1)));

        int months = 0;
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        Set<Long> funds = new HashSet<>();
        for (int year = from; year <= to; year++) {
            Optional<Path> zip;
            try {
                zip = cvm.yearly(CvmDataset.FII_MONTHLY, year);
            } catch (CvmException e) {
                log.warn("Informe FII: {} indisponivel: {}", year, e.getMessage());
                continue;
            }
            if (zip.isEmpty()) {
                continue;
            }
            List<CvmFiiMonthly> rows = new ArrayList<>(
                    CvmFiiMonthly.read(zip.get(), year, issuers::containsKey).values());
            rows.sort(Comparator.comparing(CvmFiiMonthly::cnpj).thenComparing(CvmFiiMonthly::referenceMonth));
            List<Object[]> batch = new ArrayList<>();
            int yearCreated = 0;
            int yearUpdated = 0;
            for (CvmFiiMonthly m : rows) {
                long issuerId = issuers.get(m.cnpj());
                funds.add(issuerId);
                Integer current = versions.get(new Existing(issuerId, m.referenceMonth()));
                if (current != null && current >= m.version() && !force) {
                    unchanged++;
                    continue;
                }
                if (current == null) {
                    yearCreated++;
                } else {
                    yearUpdated++;
                }
                batch.add(params(issuerId, m));
            }
            if (!batch.isEmpty()) {
                tx.executeWithoutResult(s -> jdbc.batchUpdate(UPSERT, batch));
            }
            months += rows.size();
            created += yearCreated;
            updated += yearUpdated;
            log.info("Informe FII: {}: {} meses ({} novos, {} atualizados)", year, rows.size(), yearCreated,
                    yearUpdated);
        }
        Result r = new Result(months, created, updated, unchanged, funds.size(), issuers.size() - funds.size());
        log.info("Informe FII ({}) concluido em {}s: {}", trigger, (System.currentTimeMillis() - started) / 1000, r);
        return r;
    }

    private static Object[] params(long issuerId, CvmFiiMonthly m) {
        return new Object[]{issuerId, Date.valueOf(m.referenceMonth()), m.version(), m.netAssets(), m.totalAssets(),
                m.sharesIssued(), m.navPerShare(), m.shareholders(), m.adminFeeMonth(), m.dividendYieldMonth(),
                m.amortizationMonth(), m.liquidityTotal(), m.totalInvested(), m.realEstateTotal(),
                m.realEstateCompanies(), m.receivablesTotal(), m.fiiSharesTotal(), m.liabilitiesTotal(),
                m.incomeToDistribute()};
    }
}
