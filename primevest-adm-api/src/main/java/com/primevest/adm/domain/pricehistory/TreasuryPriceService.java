package com.primevest.adm.domain.pricehistory;

import com.primevest.adm.integration.PublicFileCache;
import com.primevest.adm.integration.tesouro.TesouroPriceClient;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Preco oficial do Tesouro Direto (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R1; D1-D3).
 *
 * <p>O PU de <b>venda</b> (resgate antecipado, D2 — sem ele, o PU base) de cada titulo do catalogo vai para
 * {@code asset_price_history} ({@code source = TESOURO}) e o ultimo para {@code assets.current_price}. A partir dai
 * a app-api valoriza o Tesouro como uma cotacao (cotas x PU − custo): a rotina publica "catalogo mudou"
 * (FECHAMENTO) com os titulos cujo preco mudou.</p>
 *
 * <p><b>Casamento</b> linha ↔ titulo pelo tipo + vencimento: o tipo do Tesouro ("Tesouro IPCA+", "Tesouro Renda+
 * Aposentadoria Extra") e o ticker do catalogo sem o ano, e o vencimento e o de {@code asset_terms} — o ano do
 * ticker nem sempre e o do vencimento (Renda+ 2065 vence em 2084). Titulo que o arquivo traz e o catalogo nao tem
 * e registrado no log (titulo novo entra por migration).</p>
 *
 * <p><b>Idempotente.</b> Dia gravado com o mesmo PU nao muda; PU diferente e regravado. A carga historica comeca
 * em {@code from} (padrao 2010, D3); a rodada diaria rele so os ultimos dias.</p>
 */
@Slf4j
@Service
public class TreasuryPriceService {

    /** Mensagens de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.primevest.adm.domain.notice.AdminNotices notices;

    public static final int FIRST_YEAR = 2010;
    /** A rodada diaria rele a partir de alguns dias antes do ultimo gravado (o Tesouro corrige o arquivo). */
    static final int DAILY_OVERLAP_DAYS = 10;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final String UPSERT = """
            INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
            VALUES (?, ?, ?, 'TESOURO')
            ON CONFLICT (asset_id, reference_date) DO UPDATE SET
                close_price = EXCLUDED.close_price, source = 'TESOURO', updated_at = now()
            WHERE asset_price_history.close_price IS DISTINCT FROM EXCLUDED.close_price""";

    private final TesouroPriceClient client;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CatalogChangePublisher publisher;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired
    public TreasuryPriceService(TesouroPriceClient client, JdbcTemplate jdbc, PlatformTransactionManager txm,
                                CatalogChangePublisher publisher) {
        this(client, jdbc, txm, publisher, Clock.system(ZONE));
    }

    TreasuryPriceService(TesouroPriceClient client, JdbcTemplate jdbc, PlatformTransactionManager txm,
                         CatalogChangePublisher publisher, Clock clock) {
        this.client = client;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * @param titles     titulos do catalogo com preco no periodo
     * @param rows       dias gravados ou regravados
     * @param lastDate   data base mais recente do arquivo
     * @param priced     titulos cujo preco atual mudou (avisados a app-api)
     * @param unmatched  tipo + vencimento do arquivo sem titulo no catalogo
     */
    public record Result(int titles, int rows, LocalDate lastDate, int priced, List<String> unmatched) {
        @Override
        public String toString() {
            return titles + " titulos, " + rows + " dias gravados, ultimo " + lastDate + ", " + priced
                    + " precos atuais novos" + (unmatched.isEmpty() ? "" : ", sem titulo no catalogo: " + unmatched.size());
        }
    }

    /** Carga desde o ano {@code fromYear}. Nulo se ja ha uma rodada em andamento. */
    public Result importFrom(int fromYear, String trigger) {
        return run(LocalDate.of(Math.max(fromYear, 2002), 1, 1), trigger);
    }

    /** Rodada diaria: rele a partir de alguns dias antes do ultimo PU gravado (ou desde 2010, se nao ha nada). */
    public Result importRecent(String trigger) {
        LocalDate last = lastStored();
        return run(last == null ? LocalDate.of(FIRST_YEAR, 1, 1) : last.minusDays(DAILY_OVERLAP_DAYS), trigger);
    }

    /** Sem PU gravado ou o ultimo com mais de 4 dias (fim de semana + feriado): a subida recupera. */
    public boolean needsRecovery() {
        LocalDate last = lastStored();
        return last == null || last.isBefore(LocalDate.now(clock).minusDays(4));
    }

    private LocalDate lastStored() {
        Date d = jdbc.queryForObject("SELECT MAX(reference_date) FROM asset_price_history WHERE source = 'TESOURO'",
                Date.class);
        return d == null ? null : d.toLocalDate();
    }

    private record Key(String type, LocalDate maturity) {
    }

    private Result run(LocalDate from, String trigger) {
        if (!running.tryLock()) {
            log.warn("Tesouro ({}): ja existe uma rodada em andamento; nada feito", trigger);
            return null;
        }
        try {
            long started = System.currentTimeMillis();
            Path file;
            try {
                file = client.fetch();
            } catch (PublicFileCache.UnavailableException e) {
                log.error("Tesouro ({}): arquivo de precos indisponivel: {}", trigger, e.getMessage());
                notices.sourceUnavailable("Preço do Tesouro", "Tesouro Transparente", e.getMessage());
                throw e;
            }
            Map<Key, Long> catalog = new HashMap<>();
            jdbc.query("""
                    SELECT a.id, regexp_replace(a.ticker, ' [0-9]{4}$', '') AS type, t.maturity_date
                    FROM assets a JOIN asset_terms t ON t.asset_id = a.id
                    JOIN asset_types ty ON ty.id = a.asset_type_id WHERE ty.name = 'TESOURO_DIRETO'""",
                    rs -> { catalog.put(new Key(rs.getString("type"), rs.getDate("maturity_date").toLocalDate()),
                            rs.getLong("id")); });

            List<Object[]> batch = new ArrayList<>();
            Map<Long, TreeMap<LocalDate, BigDecimal>> latest = new HashMap<>();
            Set<String> unmatched = new TreeSet<>();
            LocalDate[] lastDate = {null};
            TesouroPriceClient.read(file, row -> {
                if (lastDate[0] == null || row.date().isAfter(lastDate[0])) {
                    lastDate[0] = row.date();
                }
                if (row.date().isBefore(from)) {
                    return;
                }
                BigDecimal pu = row.sellPu() != null ? row.sellPu() : row.basePu();
                if (pu == null) {
                    return;
                }
                Long assetId = catalog.get(new Key(row.type(), row.maturity()));
                if (assetId == null) {
                    unmatched.add(row.type() + " " + row.maturity());
                    return;
                }
                batch.add(new Object[]{assetId, Date.valueOf(row.date()), pu});
                latest.computeIfAbsent(assetId, k -> new TreeMap<>()).put(row.date(), pu);
            });

            int written = 0;
            for (int i = 0; i < batch.size(); i += 5000) {
                List<Object[]> part = batch.subList(i, Math.min(batch.size(), i + 5000));
                int[][] counts = tx.execute(s -> jdbc.batchUpdate(UPSERT, part, part.size(),
                        (ps, row) -> {
                            ps.setLong(1, (Long) row[0]);
                            ps.setDate(2, (Date) row[1]);
                            ps.setBigDecimal(3, (BigDecimal) row[2]);
                        }));
                for (int[] c : counts) {
                    for (int n : c) {
                        written += Math.max(n, 0);
                    }
                }
            }

            // preco atual = o PU do dia mais recente gravado de cada titulo
            Set<Long> priced = new HashSet<>();
            for (Map.Entry<Long, TreeMap<LocalDate, BigDecimal>> e : latest.entrySet()) {
                BigDecimal pu = e.getValue().lastEntry().getValue();
                int n = jdbc.update("""
                        UPDATE assets SET current_price = ?
                        WHERE id = ? AND current_price IS DISTINCT FROM ?
                          AND NOT EXISTS (SELECT 1 FROM asset_price_history h
                                          WHERE h.asset_id = assets.id AND h.reference_date > ?)""",
                        pu, e.getKey(), pu, Date.valueOf(e.getValue().lastKey()));
                if (n > 0) {
                    priced.add(e.getKey());
                }
            }
            if (!priced.isEmpty()) {
                publisher.publish(new CatalogChange(CatalogChange.Origin.FECHAMENTO, priced));
            }
            Result r = new Result(latest.size(), written, lastDate[0], priced.size(), List.copyOf(unmatched));
            log.info("Tesouro ({}) desde {} em {}s: {}", trigger, from, (System.currentTimeMillis() - started) / 1000, r);
            if (!unmatched.isEmpty()) {
                log.warn("Tesouro: titulos do arquivo sem titulo no catalogo (entram por migration): {}", unmatched);
            }
            List<String> semTitulo = unmatched.stream().map(String::valueOf).toList();
            semTitulo.forEach(t -> notices.pending(
                    com.primevest.domain.notification.NotificationKind.CATALOGO_TESOURO_SEM_TITULO, t,
                    "Tesouro sem título no catálogo: " + t,
                    "O arquivo de preços do Tesouro tem este título e o catálogo não. Título novo entra por migration; "
                            + "título antigo que ficou de fora de propósito pode ser dispensado.", List.of()));
            notices.resolveAllExcept(com.primevest.domain.notification.NotificationKind.CATALOGO_TESOURO_SEM_TITULO,
                    semTitulo);
            return r;
        } finally {
            running.unlock();
        }
    }
}
