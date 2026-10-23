package com.activest.adm.domain.pricehistory;

import com.activest.adm.integration.PublicFileCache;
import com.activest.adm.integration.treasury.TreasuryPriceClient;
import com.activest.adm.integration.treasury.TreasurySalesClient;
import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
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
 *
 * <p><b>Fonte</b> (docs/planos/PLANO_FONTE_TESOURO.md). Recusa ou fonte fora do ar: mensagem
 * {@code FONTE_INDISPONIVEL}; com copia em disco, a copia e lida (uma carga inicial ainda aproveita o arquivo) e o
 * resultado leva o problema em {@link Result#sourceProblem()}, que faz a tarefa terminar {@code FAILED} (D2); sem
 * copia, erro. A fonte respondeu e o dado mais recente esta mais de {@value #STALE_BUSINESS_DAYS} dias uteis atras:
 * pendencia {@code FONTE_DESATUALIZADA}, resolvida quando o dado novo chega (D3).</p>
 */
@Slf4j
@Service
public class TreasuryPriceService {

    /** Mensagens de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.activest.adm.domain.notice.AdminNotices notices;

    public static final int FIRST_YEAR = 2010;
    /** Dias uteis sem data base nova, entre a ultima e hoje, que a fonte pode ficar sem aviso (D3). */
    static final int STALE_BUSINESS_DAYS = 2;
    private static final String ROUTINE = "Preço do Tesouro";
    private static final String SOURCE = "Tesouro Transparente";
    /** A rodada diaria rele a partir de alguns dias antes do ultimo gravado (o Tesouro corrige o arquivo). */
    static final int DAILY_OVERLAP_DAYS = 10;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final String UPSERT = """
            INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
            VALUES (?, ?, ?, 'TESOURO')
            ON CONFLICT (asset_id, reference_date) DO UPDATE SET
                close_price = EXCLUDED.close_price, source = 'TESOURO', updated_at = now()
            WHERE asset_price_history.close_price IS DISTINCT FROM EXCLUDED.close_price""";

    /** Taxa do ultimo dia de cada titulo e se esta a venda (V59; benchmark padrao do Tesouro na Rentabilidade). */
    private static final String UPSERT_OFFER = """
            INSERT INTO treasury_offers (asset_id, rate_date, buy_rate, sell_rate, on_sale, sale_date)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (asset_id) DO UPDATE SET
                rate_date = EXCLUDED.rate_date, buy_rate = EXCLUDED.buy_rate, sell_rate = EXCLUDED.sell_rate,
                on_sale = EXCLUDED.on_sale, sale_date = EXCLUDED.sale_date, updated_at = now()""";
    /** Arquivo de vendas fora do ar: so a taxa muda, a oferta fica a da ultima rodada. */
    private static final String UPSERT_RATE = """
            INSERT INTO treasury_offers (asset_id, rate_date, buy_rate, sell_rate, on_sale, sale_date)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (asset_id) DO UPDATE SET
                rate_date = EXCLUDED.rate_date, buy_rate = EXCLUDED.buy_rate, sell_rate = EXCLUDED.sell_rate,
                updated_at = now()""";

    private final TreasuryPriceClient client;
    private final TreasurySalesClient sales;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CatalogChangePublisher publisher;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Autowired
    public TreasuryPriceService(TreasuryPriceClient client, TreasurySalesClient sales, JdbcTemplate jdbc,
                                PlatformTransactionManager txm, CatalogChangePublisher publisher) {
        this(client, sales, jdbc, txm, publisher, Clock.system(ZONE));
    }

    TreasuryPriceService(TreasuryPriceClient client, TreasurySalesClient sales, JdbcTemplate jdbc,
                         PlatformTransactionManager txm, CatalogChangePublisher publisher, Clock clock) {
        this.client = client;
        this.sales = sales;
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
     * @param onSale     titulos do catalogo a venda hoje (arquivo de vendas); -1 se o arquivo estava fora do ar
     * @param unmatched  tipo + vencimento do arquivo sem titulo no catalogo
     * @param sourceProblem a fonte recusou ou nao respondeu e foi lida a copia em disco (D2); nulo quando a fonte
     *                      respondeu
     * @param staleSince a data base mais recente, quando a fonte respondeu sem dado novo ha mais de
     *                   {@value #STALE_BUSINESS_DAYS} dias uteis (D3); nula quando esta em dia ou nao se sabe
     */
    public record Result(int titles, int rows, LocalDate lastDate, int priced, int onSale, List<String> unmatched,
                         String sourceProblem, LocalDate staleSince) {
        @Override
        public String toString() {
            return titles + " titulos, " + rows + " dias gravados, ultimo " + lastDate + ", " + priced
                    + " precos atuais novos, " + (onSale < 0 ? "oferta sem atualizar" : onSale + " a venda")
                    + (unmatched.isEmpty() ? "" : ", sem titulo no catalogo: " + unmatched.size())
                    + (staleSince == null ? "" : ", fonte sem dado novo desde " + staleSince)
                    + (sourceProblem == null ? "" : "; " + sourceProblem);
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
            PublicFileCache.Result fetched = client.fetch();
            String sourceProblem = null;
            if (!fetched.current()) {
                // recusa (429, pagina no lugar do arquivo), sem resposta ou 404
                String detail = fetched.detail() != null ? fetched.detail() : String.valueOf(fetched.outcome());
                notices.sourceUnavailable(ROUTINE, SOURCE, detail);
                if (fetched.file() == null) {
                    log.error("Tesouro ({}): arquivo de precos indisponivel e sem copia em disco: {}", trigger, detail);
                    throw new PublicFileCache.UnavailableException(
                            SOURCE + " indisponivel (" + detail + ") e sem copia do arquivo em disco", null);
                }
                sourceProblem = SOURCE + " indisponivel (" + detail + "); lida a copia do arquivo em disco";
                log.error("Tesouro ({}): {}", trigger, sourceProblem);
            }
            Path file = fetched.file();
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
            Map<Long, TreasuryPriceClient.Row> lastRow = new HashMap<>();
            LocalDate[] lastDate = {null};
            TreasuryPriceClient.read(file, row -> {
                if (lastDate[0] == null || row.date().isAfter(lastDate[0])) {
                    lastDate[0] = row.date();
                }
                Long assetId = catalog.get(new Key(row.type(), row.maturity()));
                if (assetId != null) {
                    lastRow.merge(assetId, row, (a, b) -> b.date().isAfter(a.date()) ? b : a);
                }
                if (row.date().isBefore(from)) {
                    return;
                }
                BigDecimal pu = row.sellPu() != null ? row.sellPu() : row.basePu();
                if (pu == null) {
                    return;
                }
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
            int onSale = offers(lastRow, catalog, trigger);
            LocalDate staleSince = fetched.current() ? staleness(lastDate[0], trigger) : null;
            Result r = new Result(latest.size(), written, lastDate[0], priced.size(), onSale, List.copyOf(unmatched),
                    sourceProblem, staleSince);
            log.info("Tesouro ({}) desde {} em {}s: {}", trigger, from, (System.currentTimeMillis() - started) / 1000, r);
            if (!unmatched.isEmpty()) {
                log.warn("Tesouro: titulos do arquivo sem titulo no catalogo (entram por migration): {}", unmatched);
            }
            List<String> withoutBond = unmatched.stream().map(String::valueOf).toList();
            withoutBond.forEach(t -> notices.pending(
                    com.activest.domain.notification.NotificationKind.CATALOGO_TESOURO_SEM_TITULO, t,
                    "Tesouro sem título no catálogo: " + t,
                    "O arquivo de preços do Tesouro tem este título e o catálogo não. Título novo entra por migration; "
                            + "título antigo que ficou de fora de propósito pode ser dispensado.", List.of()));
            notices.resolveAllExcept(com.activest.domain.notification.NotificationKind.CATALOGO_TESOURO_SEM_TITULO,
                    withoutBond);
            return r;
        } finally {
            running.unlock();
        }
    }

    /**
     * A fonte respondeu: o dado mais recente esta em dia? Mais de {@value #STALE_BUSINESS_DAYS} dias uteis entre a
     * data base mais recente e hoje (exclusive nos dois lados) abre a pendencia; em dia, resolve (D3). O arquivo sai
     * todo dia util pela manha com o D-1: em dia, a conta da 0 (ou 1 antes de o Tesouro publicar).
     *
     * @return a data base mais recente, quando atrasada; nula quando em dia
     */
    private LocalDate staleness(LocalDate lastDate, String trigger) {
        if (lastDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        int missing = lastDate.plusDays(1).isAfter(today.minusDays(1)) ? 0
                : BusinessCalendar.businessDaysBetween(lastDate.plusDays(1), today.minusDays(1));
        if (missing > STALE_BUSINESS_DAYS) {
            log.warn("Tesouro ({}): a fonte respondeu, mas o dado mais recente e de {} ({} dias uteis sem publicacao)",
                    trigger, lastDate, missing);
            notices.sourceStale(ROUTINE, SOURCE, lastDate, missing);
            return lastDate;
        }
        notices.sourceCurrent(ROUTINE, SOURCE);
        return null;
    }

    /**
     * Taxa do ultimo dia de cada titulo e a oferta de hoje ({@code treasury_offers}, V59). O arquivo de vendas fora
     * do ar nao derruba a carga: a taxa e gravada e a oferta fica a da ultima rodada (devolve -1).
     */
    private int offers(Map<Long, TreasuryPriceClient.Row> lastRow, Map<Key, Long> catalog, String trigger) {
        TreasurySalesClient.OnSale onSale = null;
        try {
            PublicFileCache.Result fetched = sales.fetch();
            if (!fetched.current()) {
                log.warn("Tesouro ({}): arquivo de vendas indisponivel ({}){}", trigger, fetched.detail(),
                        fetched.file() != null ? ", usando a copia em disco" : ", oferta sem atualizar");
            }
            if (fetched.file() != null) {
                onSale = TreasurySalesClient.onSale(fetched.file());
            }
        } catch (PublicFileCache.UnavailableException | java.io.UncheckedIOException e) {
            log.warn("Tesouro ({}): arquivo de vendas indisponivel, oferta sem atualizar: {}", trigger, e.getMessage());
        }
        Set<Long> selling = new HashSet<>();
        if (onSale != null) {
            onSale.titles().forEach(t -> {
                Long id = catalog.get(new Key(t.type(), t.maturity()));
                if (id != null) {
                    selling.add(id);
                }
            });
        }
        LocalDate saleDate = onSale != null ? onSale.lastDate() : null;
        List<Object[]> rows = new ArrayList<>();
        lastRow.forEach((id, row) -> rows.add(new Object[]{id, Date.valueOf(row.date()), row.buyRate(), row.sellRate(),
                selling.contains(id), saleDate != null ? Date.valueOf(saleDate) : null}));
        String sql = onSale != null ? UPSERT_OFFER : UPSERT_RATE;
        tx.executeWithoutResult(s -> jdbc.batchUpdate(sql, rows));
        return onSale != null ? selling.size() : -1;
    }
}
