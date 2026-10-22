package com.primevest.adm.domain.fundamentals;

import com.primevest.domain.task.TaskRun;

import com.primevest.domain.issuer.StatementLayout;
import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmException;
import com.primevest.adm.integration.cvm.CvmFreCapital;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import com.primevest.adm.integration.cvm.CvmStatements;
import com.primevest.adm.integration.cvm.CvmStatements.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Balancos da CVM — DFP e ITR — em {@code cvm_documents}, {@code cvm_statement_values}
 * e {@code share_counts} (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F5).
 *
 * <p>So as companhias emissoras ({@code issuers}); o resto do arquivo e descartado
 * na leitura. Por documento: consolidado ou, na falta, individual (D6); as
 * metricas de {@link FinancialMetricRules}; a quantidade de acoes da composicao do
 * capital (2020+, escala conferida por {@link ShareScale}) ou, antes, do historico
 * de capital do FRE.</p>
 *
 * <p><b>Idempotente.</b> Documento com a mesma versao ja gravada e pulado; versao
 * nova (reapresentacao) substitui as metricas e a quantidade. {@code force}
 * reprocessa tudo — e o caminho para uma regra nova ({@code carga/data/cvm} ja tem os
 * arquivos, nada e baixado de novo).</p>
 */
@Slf4j
@Service
public class FinancialStatementImportService {

    /** "Catalogo mudou" para a carteira (docs/planos/PLANO_NOTIFICACOES.md: alerta de preco e "padrao mudou"). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher catalogChangePublisher;

    public static final int FIRST_YEAR = 2010;
    /** O ITR comeca em 2011 nos dados abertos. */
    private static final int FIRST_ITR_YEAR = 2011;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final CvmOpenDataClient cvm;
    private final CompanyQuarterService quarters;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired
    public FinancialStatementImportService(CvmOpenDataClient cvm, CompanyQuarterService quarters, JdbcTemplate jdbc,
                                           PlatformTransactionManager txm) {
        this(cvm, quarters, jdbc, txm, Clock.system(ZONE));
    }

    FinancialStatementImportService(CvmOpenDataClient cvm, CompanyQuarterService quarters, JdbcTemplate jdbc,
                                    PlatformTransactionManager txm, Clock clock) {
        this.cvm = cvm;
        this.quarters = quarters;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
    }

    /**
     * {@code shareCounts}: fonte e situacao da quantidade de acoes dos documentos gravados nesta carga,
     * antes da passada pelo vizinho; {@code resolvedByNeighbor}: quantos REVISAR essa passada resolveu;
     * {@code pendingReview}: quantos seguem REVISAR na base inteira; {@code quarters}: linhas da serie
     * trimestral recalculada ao fim da carga ({@link CompanyQuarterService}).
     */
    public record Result(int documents, int created, int updated, int unchanged, int noData,
                         Map<String, Integer> shareCounts, int resolvedByNeighbor, int pendingReview, int layouts,
                         int quarters) {
    }

    /** Ano corrente e anterior — o que a CVM ainda recebe (rotina diaria). */
    public Result importRecent(String trigger) {
        int year = LocalDate.now(clock).getYear();
        return importYears(year - 1, year, false, trigger);
    }

    public Result importYears(int from, int to, boolean force, String trigger) {
        if (!running.tryLock()) {
            log.warn("Balancos CVM ({}): ja existe uma carga rodando; nada feito", trigger);
            return null;
        }
        try {
            return run(Math.max(from, FIRST_YEAR), to, force, trigger);
        } finally {
            running.unlock();
        }
    }

    private Result run(int from, int to, boolean force, String trigger) {
        long started = System.currentTimeMillis();
        log.info("Balancos CVM ({}): {} a {}{}", trigger, from, to, force ? ", reprocessando tudo" : "");
        Map<String, Long> issuers = new HashMap<>();
        jdbc.query("SELECT id, cnpj FROM issuers WHERE kind = 'COMPANY'",
                rs -> { issuers.put(rs.getString("cnpj"), rs.getLong("id")); });
        FreCapitalHistory fre = loadFre(Math.max(to, LocalDate.now(clock).getYear()));

        Counter total = new Counter();
        Map<Long, Latest> layouts = new HashMap<>();
        for (int year = from; year <= to; year++) {
            TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
            for (CvmDataset dataset : List.of(CvmDataset.DFP, CvmDataset.ITR)) {
                if (dataset == CvmDataset.ITR && year < FIRST_ITR_YEAR) {
                    continue;
                }
                Optional<Path> zip;
                try {
                    zip = cvm.yearly(dataset, year);
                } catch (CvmException e) {
                    log.warn("Balancos CVM: {} {} indisponivel: {}", dataset, year, e.getMessage());
                    continue;
                }
                if (zip.isEmpty()) {
                    continue;
                }
                long t0 = System.currentTimeMillis();
                List<Document> docs = new ArrayList<>(
                        CvmStatements.read(zip.get(), dataset.folder(), year, issuers::containsKey).values());
                docs.sort(Comparator.comparing(Document::referenceDate).thenComparing(Document::cnpj));
                Counter c = new Counter();
                for (Document d : docs) {
                    TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
                    long issuerId = issuers.get(d.cnpj());
                    Outcome o = importDocument(d, dataset.name(), issuerId, fre, force);
                    c.add(o);
                    if (o.layout() != null) {
                        layouts.merge(issuerId, new Latest(d.referenceDate(), o.layout()),
                                (a, b) -> b.date().isAfter(a.date()) ? b : a);
                    }
                }
                total.addAll(c);
                log.info("Balancos CVM: {} {}: {} documentos ({} novos, {} atualizados, {} iguais, {} sem dados) em {}s",
                        dataset, year, docs.size(), c.created, c.updated, c.unchanged, c.noData,
                        (System.currentTimeMillis() - t0) / 1000);
            }
        }
        int layoutsSet = 0;
        for (Map.Entry<Long, Latest> e : layouts.entrySet()) {
            layoutsSet += jdbc.update("""
                    UPDATE issuers SET statement_layout = ?, updated_at = now()
                    WHERE id = ? AND statement_layout IS DISTINCT FROM ?""",
                    e.getValue().layout().name(), e.getKey(), e.getValue().layout().name());
        }
        int byNeighbor = resolveByNeighbors();
        int byEvent = resolveByStockEvents();
        if (byEvent > 0) {
            // um documento resolvido pelo evento pode ser o vizinho do seguinte
            byNeighbor += resolveByNeighbors();
        }
        byNeighbor += byEvent;
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM share_counts WHERE scale_status = 'REVISAR'",
                Integer.class);
        CompanyQuarterService.Result series = quarters.rebuild(trigger);
        Result r = new Result(total.documents, total.created, total.updated, total.unchanged, total.noData,
                total.shares, byNeighbor, pending == null ? 0 : pending, layoutsSet,
                series.quarters());
        log.info("Balancos CVM ({}) concluido em {}s: {}", trigger, (System.currentTimeMillis() - started) / 1000, r);
        // Trimestre novo ou reapresentado: a carteira confere o "padrao mudou" do Preco Teto (lista vazia = todos).
        if (catalogChangePublisher != null && total.created + total.updated > 0) {
            try {
                catalogChangePublisher.publish(new com.primevest.domain.catalog.CatalogChange(
                        com.primevest.domain.catalog.CatalogChange.Origin.BALANCOS, java.util.Set.of()));
            } catch (RuntimeException e) {
                log.warn("Balancos CVM: aviso a carteira nao publicado: {}", e.getMessage());
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ documento

    private record Latest(LocalDate date, StatementLayout layout) {
    }

    private enum Kind { CREATED, UPDATED, UNCHANGED, NO_DATA }

    private record Outcome(Kind kind, StatementLayout layout, String shares) {
    }

    private record Existing(long id, int version) {
    }

    private Outcome importDocument(Document d, String docType, long issuerId, FreCapitalHistory fre, boolean force) {
        boolean consolidated = !d.consolidated().isEmpty();
        List<CvmStatements.Line> lines = consolidated ? d.consolidated() : d.individual();
        if (lines.isEmpty() && d.capital() == null) {
            return new Outcome(Kind.NO_DATA, null, null);
        }
        StatementLayout layout = lines.isEmpty() ? null : FinancialMetricRules.layoutOf(lines);
        List<Existing> found = jdbc.query("""
                SELECT id, version FROM cvm_documents WHERE issuer_id = ? AND doc_type = ? AND reference_date = ?""",
                (rs, i) -> new Existing(rs.getLong("id"), rs.getInt("version")),
                issuerId, docType, Date.valueOf(d.referenceDate()));
        Existing existing = found.isEmpty() ? null : found.get(0);
        if (existing != null && existing.version() >= d.version() && !force) {
            return new Outcome(Kind.UNCHANGED, layout, null);
        }
        List<FinancialMetricRules.Value> values = lines.isEmpty() ? List.of()
                : FinancialMetricRules.extract(lines, layout);
        ShareRow shares = shares(d, values, fre);

        tx.executeWithoutResult(status -> {
            Long id = jdbc.queryForObject("""
                    INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, cvm_doc_id, received_at,
                                               consolidated)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (issuer_id, doc_type, reference_date) DO UPDATE SET
                        version = EXCLUDED.version, cvm_doc_id = EXCLUDED.cvm_doc_id,
                        received_at = EXCLUDED.received_at, consolidated = EXCLUDED.consolidated, updated_at = now()
                    RETURNING id""", Long.class,
                    issuerId, docType, Date.valueOf(d.referenceDate()), d.version(), d.cvmDocId(),
                    d.receivedAt() == null ? null : Date.valueOf(d.receivedAt()), consolidated);
            jdbc.update("DELETE FROM cvm_statement_values WHERE document_id = ?", id);
            jdbc.update("DELETE FROM share_counts WHERE document_id = ?", id);
            jdbc.batchUpdate("""
                    INSERT INTO cvm_statement_values (document_id, metric, period_start, period_end, value)
                    VALUES (?, ?, ?, ?, ?)""",
                    values.stream().map(v -> new Object[]{id, v.metric().name(),
                            v.periodStart() == null ? null : Date.valueOf(v.periodStart()),
                            Date.valueOf(v.periodEnd()), v.value()}).toList());
            if (shares != null) {
                jdbc.update("""
                        INSERT INTO share_counts (document_id, source, ordinary_shares, preferred_shares, total_shares,
                                                  ordinary_treasury, preferred_treasury, total_treasury,
                                                  scale_factor, scale_status, scale_check)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        id, shares.source(), shares.ordinary(), shares.preferred(), shares.total(),
                        shares.ordinaryTreasury(), shares.preferredTreasury(), shares.treasury(),
                        shares.factor(), shares.status().name(), shares.check() == null ? null : shares.check().name());
            }
        });
        return new Outcome(existing == null ? Kind.CREATED : Kind.UPDATED, layout,
                shares == null ? "SEM_QUANTIDADE" : shares.source() + "_" + shares.status());
    }

    // ------------------------------------------------------------------ quantidade de acoes

    record ShareRow(String source, Long ordinary, Long preferred, long total, Long ordinaryTreasury,
                    Long preferredTreasury, Long treasury, Integer factor, ShareScale.Status status,
                    ShareScale.Check check) {
    }

    private ShareRow shares(Document d, List<FinancialMetricRules.Value> values, FreCapitalHistory fre) {
        BigDecimal profit = accumulated(values, FinancialMetric.LUCRO_CONTROLADORA, d.referenceDate());
        BigDecimal eps = accumulated(values, FinancialMetric.LPA_ON, d.referenceDate());
        if (eps == null) {
            eps = accumulated(values, FinancialMetric.LPA_PN, d.referenceDate());
        }
        CvmFreCapital.Event capital = fre.at(d.cnpj(), d.referenceDate());
        CvmStatements.CapitalComposition c = d.capital();
        if (c != null && c.total() > 0) {
            long outstanding = c.total() - c.treasury();
            Long freTotal = capital == null ? null : capital.total();
            ShareScale.Decision decision = ShareScale.decide(c.total(), outstanding, freTotal, profit, eps);
            return new ShareRow("COMPOSICAO", c.ordinary(), c.preferred(), c.total(), c.ordinaryTreasury(),
                    c.preferredTreasury(), c.treasury(), decision.factor(), decision.status(), decision.check());
        }
        if (capital == null) {
            return null;
        }
        // FRE vem em unidades e o LPA so confirma. Quando os dois discordam por mil, nao ha como saber qual
        // errou (Banrisul 2010: LPA 0,00186, em milhar; LPS 2010: FRE 29.343 acoes, em milhar): REVISAR,
        // e a passada pelo vizinho decide.
        Integer byEps = ShareScale.byEps(capital.total(), profit, eps);
        boolean contradicted = byEps != null && byEps != 1;
        return new ShareRow("FRE", capital.ordinary(), capital.preferred(), capital.total(), null, null, null,
                contradicted ? null : 1, contradicted ? ShareScale.Status.REVISAR : ShareScale.Status.OK,
                byEps != null && byEps == 1 ? ShareScale.Check.LPA : null);
    }

    /** Valor do periodo mais longo terminado na data (o acumulado do exercicio). */
    static BigDecimal accumulated(List<FinancialMetricRules.Value> values, FinancialMetric metric, LocalDate end) {
        return values.stream()
                .filter(v -> v.metric() == metric && v.periodEnd().equals(end) && v.periodStart() != null)
                .min(Comparator.comparing(FinancialMetricRules.Value::periodStart))
                .map(FinancialMetricRules.Value::value).orElse(null);
    }

    /**
     * Segunda passada da escala: quantidade sem conferencia ({@code REVISAR}) e
     * comparada com a do documento validado mais proximo no tempo, anterior ou
     * seguinte, da mesma companhia (mesma janela de {@link ShareScale}: razao entre
     * 0,5 e 2, um so candidato). Resolve os primeiros documentos de companhia
     * recem-listada, ainda sem FRE nem LPA, e FRE contradito pelo LPA. Repete
     * enquanto resolver algum (um documento resolvido pode ser o vizinho do
     * seguinte). Erro na fonte e grupamento no meio continuam {@code REVISAR}.
     *
     * @return quantos foram resolvidos
     */
    public int resolveByNeighbors() {
        int resolved = 0;
        for (int round = 0; round < 10; round++) {
            int n = jdbc.update("""
                    WITH pending AS (
                        SELECT s.document_id, d.issuer_id, d.reference_date, s.total_shares::numeric AS raw
                        FROM share_counts s JOIN cvm_documents d ON d.id = s.document_id
                        WHERE s.scale_status = 'REVISAR' AND s.total_shares > 0),
                    near AS (
                        SELECT p.*, (SELECT s2.total_shares::numeric * s2.scale_factor
                                     FROM share_counts s2 JOIN cvm_documents d2 ON d2.id = s2.document_id
                                     WHERE d2.issuer_id = p.issuer_id AND s2.scale_factor IS NOT NULL
                                     ORDER BY abs(d2.reference_date - p.reference_date), d2.reference_date
                                     LIMIT 1) AS neighbor
                        FROM pending p),
                    decided AS (
                        SELECT document_id,
                               CASE WHEN raw / neighbor BETWEEN 0.5 AND 2
                                         AND NOT raw * 1000 / neighbor BETWEEN 0.5 AND 2 THEN 1
                                    WHEN raw * 1000 / neighbor BETWEEN 0.5 AND 2
                                         AND NOT raw / neighbor BETWEEN 0.5 AND 2 THEN 1000
                               END AS factor
                        FROM near WHERE neighbor > 0)
                    UPDATE share_counts s
                    SET scale_factor = decided.factor, scale_status = 'OK', scale_check = 'VIZINHO'
                    FROM decided WHERE s.document_id = decided.document_id AND decided.factor IS NOT NULL""");
            resolved += n;
            if (n == 0) {
                break;
            }
        }
        if (resolved > 0) {
            log.info("Balancos CVM: {} quantidades de acoes resolvidas pelo documento vizinho", resolved);
        }
        return resolved;
    }

    /**
     * Terceira passada da escala (F9, PENDENCIAS 18.11): grupamento ou desdobramento
     * entre dois balancos. A quantidade que ficou em {@code REVISAR} e comparada com a do
     * documento validado anterior da mesma companhia multiplicada pelos eventos
     * societarios registrados ({@code asset_stock_events} dos ativos do emissor) entre as
     * duas datas — grupamento pelo fator, desdobramento e bonificacao por 1 + fator/100.
     * Mesma janela de {@link ShareScale} (razao entre 0,5 e 2, um so candidato). Primeiro
     * com os eventos ate a data do balanco; o que sobrar, com os eventos ate a entrega do
     * documento — a companhia costuma informar a quantidade ja grupada quando o grupamento
     * sai entre o fim do trimestre e a entrega (Azevedo & Travassos Energia 06/2026).
     * Depende dos eventos da rotina de proventos: o que nao fechar hoje fecha na rodada
     * seguinte dos balancos.
     *
     * @return quantos foram resolvidos
     */
    public int resolveByStockEvents() {
        int resolved = 0;
        for (boolean untilDelivery : new boolean[]{false, true}) {
            resolved += jdbc.update("""
                    WITH pending AS (
                        SELECT s.document_id, d.issuer_id, d.reference_date, s.total_shares::numeric AS raw,
                               CASE WHEN ? THEN COALESCE(d.received_at::date, d.reference_date + 90)
                                    ELSE d.reference_date END AS until_date
                        FROM share_counts s JOIN cvm_documents d ON d.id = s.document_id
                        WHERE s.scale_status = 'REVISAR' AND s.total_shares > 0),
                    prev AS (
                        SELECT p.*, v.count AS prev_count, v.reference_date AS prev_date
                        FROM pending p JOIN LATERAL (
                            SELECT s2.total_shares::numeric * s2.scale_factor AS count, d2.reference_date
                            FROM share_counts s2 JOIN cvm_documents d2 ON d2.id = s2.document_id
                            WHERE d2.issuer_id = p.issuer_id AND s2.scale_factor IS NOT NULL
                              AND d2.reference_date < p.reference_date
                            ORDER BY d2.reference_date DESC LIMIT 1) v ON true
                        WHERE v.count > 0),
                    events AS (
                        SELECT p.document_id,
                               exp(sum(ln(CASE e.type WHEN 'GRUPAMENTO' THEN e.factor ELSE 1 + e.factor / 100 END)))
                                   AS ratio
                        FROM prev p JOIN LATERAL (
                            SELECT DISTINCT ev.type, ev.data_com, ev.factor
                            FROM asset_stock_events ev JOIN assets a ON a.id = ev.asset_id
                            WHERE a.issuer_id = p.issuer_id AND ev.factor > 0
                              AND ev.data_com > p.prev_date AND ev.data_com <= p.until_date) e ON true
                        GROUP BY p.document_id),
                    expected AS (
                        SELECT p.document_id, p.raw, p.prev_count * ev.ratio AS count
                        FROM prev p JOIN events ev ON ev.document_id = p.document_id),
                    decided AS (
                        SELECT document_id,
                               CASE WHEN raw / count BETWEEN 0.5 AND 2
                                         AND NOT raw * 1000 / count BETWEEN 0.5 AND 2 THEN 1
                                    WHEN raw * 1000 / count BETWEEN 0.5 AND 2
                                         AND NOT raw / count BETWEEN 0.5 AND 2 THEN 1000
                               END AS factor
                        FROM expected WHERE count > 0)
                    UPDATE share_counts s
                    SET scale_factor = decided.factor, scale_status = 'OK', scale_check = 'EVENTO'
                    FROM decided WHERE s.document_id = decided.document_id AND decided.factor IS NOT NULL""",
                    untilDelivery);
        }
        if (resolved > 0) {
            log.info("Balancos CVM: {} quantidades de acoes resolvidas por grupamento/desdobramento registrado", resolved);
        }
        return resolved;
    }

    private FreCapitalHistory loadFre(int lastYear) {
        List<CvmFreCapital.Event> events = new ArrayList<>();
        for (int y = FIRST_YEAR; y <= lastYear; y++) {
            try {
                Optional<Path> zip = cvm.yearly(CvmDataset.FRE, y);
                if (zip.isPresent()) {
                    events.addAll(CvmFreCapital.read(zip.get(), y));
                }
            } catch (CvmException e) {
                log.warn("Balancos CVM: FRE {} indisponivel: {}", y, e.getMessage());
            }
        }
        FreCapitalHistory history = new FreCapitalHistory(events);
        log.info("Balancos CVM: historico de capital do FRE com {} companhias ({} eventos)",
                history.companies(), events.size());
        return history;
    }

    // ------------------------------------------------------------------ contagem

    private static final class Counter {
        int documents;
        int created;
        int updated;
        int unchanged;
        int noData;
        final Map<String, Integer> shares = new TreeMap<>();

        void add(Outcome o) {
            documents++;
            switch (o.kind()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
                case NO_DATA -> noData++;
            }
            if (o.shares() != null) {
                shares.merge(o.shares(), 1, Integer::sum);
            }
        }

        void addAll(Counter c) {
            documents += c.documents;
            created += c.created;
            updated += c.updated;
            unchanged += c.unchanged;
            noData += c.noData;
            c.shares.forEach((k, v) -> shares.merge(k, v, Integer::sum));
        }
    }
}
