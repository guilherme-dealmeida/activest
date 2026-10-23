package com.primevest.adm.domain.dividend;

import com.primevest.domain.task.TaskRun;

import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import com.primevest.adm.integration.cvm.CvmDividendReport;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Data de pagamento dos proventos de acao pelos "Relatorios Proventos" da CVM
 * (docs/planos/PLANO_CARGA_INICIAL.md, 5.2 — P3; regra D: informacao do catalogo completa).
 *
 * <p>O historico longo da B3 ({@code B3_HISTORICO}) nao traz a data de pagamento. De 2022 em
 * diante a companhia entrega a CVM um relatorio padronizado por provento (ISIN, valor bruto por
 * acao, ultimo dia "com", data de pagamento ou parcelas). Esta rotina le o indice anual do IPE
 * ({@code carga/data/cvm/ipe}), baixa cada relatorio uma vez ({@code carga/data/cvm/proventos}, o arquivo e o
 * estado) e completa a data dos eventos do catalogo <b>sem data</b> que batem em ISIN, data-com e
 * valor — com {@code payment_date_source = 'CVM'}. A data que vier depois da propria fonte do
 * evento (B3) substitui esta ({@code CashEventWriter}).</p>
 *
 * <p>Parcelado: cada parcela datada completa a parcela do evento com o mesmo valor, na ordem
 * (seq 1, 2...). Parcela "a definir" fica sem data. Nada e apagado; rodar de novo so completa o
 * que falta.</p>
 */
@Slf4j
@Service
public class CvmPaymentDateService {

    /** O relatorio de proventos existe no IPE de 2022 em diante. */
    public static final int FIRST_YEAR = 2022;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000001");

    private final CvmOpenDataClient cvm;
    private final JdbcTemplate jdbc;
    private final CatalogChangePublisher catalogPublisher;
    private final int parallel;
    private final ReentrantLock running = new ReentrantLock();

    public CvmPaymentDateService(CvmOpenDataClient cvm, JdbcTemplate jdbc, CatalogChangePublisher catalogPublisher,
                                 @Value("${cvm.rad-parallel-downloads:4}") int parallel) {
        this.cvm = cvm;
        this.jdbc = jdbc;
        this.catalogPublisher = catalogPublisher;
        this.parallel = Math.max(1, parallel);
    }

    public record Result(int reports, int downloaded, int unreadable, int items, int filled, Set<Long> assets) {
        @Override
        public String toString() {
            return reports + " relatorios (" + downloaded + " baixados, " + unreadable + " ilegiveis), "
                    + items + " proventos lidos, " + filled + " datas de pagamento completadas em "
                    + assets.size() + " ativos";
        }
    }

    /** Anos {@code from..to}. Se outra rodada estiver em andamento, devolve {@code null}. */
    public Result run(int from, int to, String reason) {
        if (!running.tryLock()) {
            log.warn("Datas de pagamento CVM ({}): ja existe uma rodada em andamento; nada feito", reason);
            return null;
        }
        try {
            int reports = 0;
            AtomicInteger downloaded = new AtomicInteger();
            int unreadable = 0;
            int items = 0;
            int filled = 0;
            Set<Long> assets = new HashSet<>();
            for (int year = Math.max(from, FIRST_YEAR); year <= to; year++) {
                TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
                Optional<Path> zip = cvm.yearly(CvmDataset.IPE, year);
                if (zip.isEmpty()) {
                    continue;
                }
                List<CvmDividendReport.Entry> entries = CvmDividendReport.entries(zip.get(), year);
                List<Path> pdfs = download(entries, downloaded);
                for (Path pdf : pdfs) {
                    TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
                    reports++;
                    CvmDividendReport.Report report;
                    try {
                        report = CvmDividendReport.parse(CvmDividendReport.text(pdf));
                    } catch (RuntimeException e) {
                        unreadable++;
                        log.debug("Datas de pagamento CVM: {} ilegivel: {}", pdf.getFileName(), e.getMessage());
                        continue;
                    }
                    if (report.dataCom() == null) {
                        unreadable++;
                        continue;
                    }
                    for (CvmDividendReport.Item item : report.items()) {
                        items++;
                        filled += fill(report.dataCom(), item, assets);
                    }
                }
                log.info("Datas de pagamento CVM ({}): {} — {} relatorios", reason, year, entries.size());
            }
            Result r = new Result(reports, downloaded.get(), unreadable, items, filled, assets);
            log.info("Datas de pagamento CVM ({}) concluido: {}", reason, r);
            if (!assets.isEmpty()) {
                catalogPublisher.publish(new CatalogChange(CatalogChange.Origin.PROVENTOS, assets));
            }
            return r;
        } finally {
            running.unlock();
        }
    }

    /** PDFs dos relatorios: os que estao em disco e os baixados agora (no maximo {@code parallel} juntos). */
    private List<Path> download(List<CvmDividendReport.Entry> entries, AtomicInteger downloaded) {
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            List<Future<Optional<Path>>> futures = new ArrayList<>();
            for (CvmDividendReport.Entry e : entries) {
                // TaskRun.propagate: o log do download entra no log da rodada (D6)
                futures.add(pool.submit(TaskRun.propagateCall(() -> {
                    Path expected = cvm.dir().resolve("proventos").resolve(e.protocol() + "_" + e.version() + ".pdf");
                    boolean had = java.nio.file.Files.isRegularFile(expected);
                    Optional<Path> p = cvm.radDocument(e.link(), e.protocol(), e.version());
                    if (!had && p.isPresent()) {
                        downloaded.incrementAndGet();
                    }
                    return p;
                })));
            }
            List<Path> out = new ArrayList<>();
            for (Future<Optional<Path>> f : futures) {
                try {
                    f.get().ifPresent(out::add);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    // RAD fora do ar para um documento: fica para a proxima rodada
                    log.debug("Datas de pagamento CVM: relatorio nao obtido: {}", ex.getMessage());
                }
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Completa os eventos sem data que batem com o provento; devolve quantos. */
    private int fill(LocalDate dataCom, CvmDividendReport.Item item, Set<Long> assets) {
        if (item.installments()) {
            int n = 0;
            short seq = 1;
            for (CvmDividendReport.Parcel p : item.parcels()) {
                if (p.paymentDate() != null) {
                    n += update(item.isin(), dataCom, p.value(), seq, p.paymentDate(), assets);
                }
                seq++;
            }
            return n;
        }
        if (item.paymentDate() == null) {
            return 0;
        }
        return update(item.isin(), dataCom, item.value(), null, item.paymentDate(), assets);
    }

    private int update(String isin, LocalDate dataCom, BigDecimal value, Short seq, LocalDate paymentDate,
                       Set<Long> assets) {
        List<Long> ids = jdbc.queryForList("""
                SELECT e.asset_id FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
                WHERE COALESCE(e.isin, a.isin) = ? AND e.data_com = ? AND e.payment_date IS NULL
                  AND ABS(e.value_per_share - ?) <= ? AND (CAST(? AS SMALLINT) IS NULL OR e.seq = ?)""",
                Long.class, isin, java.sql.Date.valueOf(dataCom), value, TOLERANCE, seq, seq);
        if (ids.isEmpty()) {
            return 0;
        }
        int n = jdbc.update("""
                UPDATE asset_cash_events e SET payment_date = ?, payment_date_source = 'CVM'
                FROM assets a
                WHERE a.id = e.asset_id AND COALESCE(e.isin, a.isin) = ? AND e.data_com = ? AND e.payment_date IS NULL
                  AND ABS(e.value_per_share - ?) <= ? AND (CAST(? AS SMALLINT) IS NULL OR e.seq = ?)""",
                java.sql.Date.valueOf(paymentDate), isin, java.sql.Date.valueOf(dataCom), value, TOLERANCE, seq, seq);
        assets.addAll(ids);
        return n;
    }
}
