package com.primevest.adm.domain.pricehistory;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.adm.integration.b3.B3HistoricalQuotesClient;
import com.primevest.adm.integration.b3.CotahistQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Carga da serie historica da B3 (COTAHIST) em {@code asset_price_history}
 * (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F2).
 *
 * <ul>
 *   <li>So ACAO (lote padrao e fracionario) e FII do catalogo.</li>
 *   <li>O ticker do arquivo casa com o do catalogo; linha a vista com ticker que
 *   o catalogo nao tem casa pelo ISIN, quando o ISIN e de um ativo do catalogo
 *   (ticker renomeado com o mesmo ISIN). Os anos sao lidos do mais recente para
 *   o mais antigo, para o ISIN atual ja ser conhecido. O fracionario nunca e
 *   destino dessa busca: tem o mesmo ISIN do lote padrao.</li>
 *   <li>Upsert por (ativo, dia) com {@code source = B3_COTAHIST}: a serie oficial
 *   vence o que a BRAPI tiver gravado, e a BRAPI nao volta a sobrescrever.</li>
 *   <li>Preenche {@code assets.isin} com o ISIN do pregao mais recente de cada
 *   ticker do catalogo.</li>
 * </ul>
 *
 * <p>Idempotente: rodar de novo regrava os mesmos valores. Os zips ficam em
 * {@code carga/data/b3/cotahist}; ano fechado em disco nao vai a rede.</p>
 */
@Slf4j
@Service
public class HistoricalQuotesImportService {

    /** "Catalogo mudou" para a carteira (docs/planos/PLANO_NOTIFICACOES.md: alerta de preco e "padrao mudou"). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher catalogChangePublisher;

    public static final int FIRST_YEAR = 2010;
    private static final Set<String> TYPES = Set.of("ACAO", "FII");
    private static final int BATCH = 5000;
    private static final String UPSERT = """
            INSERT INTO asset_price_history
                (asset_id, reference_date, open_price, high_price, low_price, close_price, volume, source,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'B3_COTAHIST', NOW(), NOW())
            ON CONFLICT (asset_id, reference_date) DO UPDATE SET
                open_price = EXCLUDED.open_price, high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price, close_price = EXCLUDED.close_price,
                volume = EXCLUDED.volume, source = 'B3_COTAHIST', updated_at = NOW()
            """;
    /** Linha achada pelo ISIN (ticker antigo) nunca passa por cima da linha do proprio ticker. */
    private static final String INSERT_IF_ABSENT = """
            INSERT INTO asset_price_history
                (asset_id, reference_date, open_price, high_price, low_price, close_price, volume, source,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'B3_COTAHIST', NOW(), NOW())
            ON CONFLICT (asset_id, reference_date) DO NOTHING
            """;

    private final B3HistoricalQuotesClient client;
    private final AssetRepository assetRepository;
    private final JdbcTemplate jdbc;

    public HistoricalQuotesImportService(B3HistoricalQuotesClient client, AssetRepository assetRepository,
                                         JdbcTemplate jdbc) {
        this.client = client;
        this.assetRepository = assetRepository;
        this.jdbc = jdbc;
    }

    public record YearResult(int year, boolean found, long quotes, long byIsin) {
    }

    public record Result(List<YearResult> years, long quotes, int isinsUpdated) {
    }

    /** Anos {@code from} a {@code to}, inclusive. */
    public synchronized Result importYears(int from, int to, String reason) {
        log.info("Serie historica B3 ({}): {} a {}", reason, from, to);
        Catalog catalog = catalog();
        List<YearResult> years = new ArrayList<>();
        long total = 0;
        // Do mais recente para o mais antigo: o ano novo ensina o ISIN atual de cada
        // ticker, e os anos antigos usam esse ISIN para achar o ticker de antes da
        // renomeacao.
        for (int year = to; year >= from; year--) {
            Optional<java.nio.file.Path> zip = client.year(year);
            if (zip.isEmpty()) {
                log.warn("Serie historica B3: {} nao existe na B3", year);
                years.add(new YearResult(year, false, 0, 0));
                continue;
            }
            long[] counts = importZip(zip.get(), catalog);
            total += counts[0];
            years.add(new YearResult(year, true, counts[0], counts[1]));
            log.info("Serie historica B3: {} — {} pregoes gravados ({} pelo ISIN)", year, counts[0], counts[1]);
        }

        int isins = updateIsins(catalog.latestIsin());
        Result r = new Result(years, total, isins);
        log.info("Serie historica B3 ({}) concluida: {} pregoes, {} ISINs gravados no catalogo", reason, total, isins);
        return r;
    }

    // ------------------------------------------------------------------ pregao a pregao (PLANO_PRECO_TETO, 4.5)

    /** Lacuna maior que isso usa o zip do ano (um arquivo) em vez de um arquivo por pregao. */
    static final int MAX_DAILY_GAP_DAYS = 31;

    public record DayResult(LocalDate date, boolean found, long quotes) {
    }

    public record DaysResult(LocalDate from, LocalDate to, List<DayResult> days, long quotes, int isinsUpdated) {
    }

    /**
     * Rotina diaria: do pregao seguinte ao ultimo gravado pela B3 ({@code source = B3_COTAHIST}) ate hoje, um
     * arquivo por pregao. Sem nada gravado, ou com lacuna maior que {@value #MAX_DAILY_GAP_DAYS} dias, cai para o
     * zip do ano ({@link #importYears}). O dia cujo arquivo ainda nao saiu fica para a rodada seguinte.
     */
    public DaysResult importRecent(String reason) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate last = lastB3Day();
        if (last == null || last.isBefore(today.minusDays(MAX_DAILY_GAP_DAYS))) {
            int fromYear = last == null ? today.getYear() : last.getYear();
            log.info("Cotacoes B3 ({}): ultimo pregao gravado {} — lacuna longa, zip do ano", reason, last);
            Result y = importYears(fromYear, today.getYear(), reason);
            return new DaysResult(last, today, List.of(), y.quotes(), y.isinsUpdated());
        }
        return importDays(last.plusDays(1), today, reason);
    }

    /** Pregoes de {@code from} a {@code to}, inclusive (fim de semana nao vai a B3). */
    public synchronized DaysResult importDays(LocalDate from, LocalDate to, String reason) {
        Catalog catalog = catalog();
        List<DayResult> days = new ArrayList<>();
        long total = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                continue;
            }
            Optional<java.nio.file.Path> zip = client.day(d);
            if (zip.isEmpty()) {
                days.add(new DayResult(d, false, 0));
                continue;
            }
            long[] counts = importZip(zip.get(), catalog);
            total += counts[0];
            days.add(new DayResult(d, true, counts[0]));
        }
        int isins = updateIsins(catalog.latestIsin());
        long found = days.stream().filter(DayResult::found).count();
        // Preco novo de todo o catalogo: a carteira confere o alerta de preco do Preco Teto (secao 5.5).
        if (total > 0 && catalogChangePublisher != null) {
            try {
                java.util.Set<Long> ids = new java.util.HashSet<>(jdbc.queryForList(
                        "SELECT DISTINCT asset_id FROM asset_price_history WHERE source = 'B3_COTAHIST' "
                                + "AND reference_date BETWEEN ? AND ?", Long.class, java.sql.Date.valueOf(from),
                        java.sql.Date.valueOf(to)));
                if (!ids.isEmpty()) {
                    catalogChangePublisher.publish(new com.primevest.domain.catalog.CatalogChange(
                            com.primevest.domain.catalog.CatalogChange.Origin.FECHAMENTO, ids));
                }
            } catch (RuntimeException e) {
                log.warn("Cotacoes B3: aviso a carteira nao publicado: {}", e.getMessage());
            }
        }
        if (found > 0 || !days.isEmpty()) {
            log.info("Cotacoes B3 ({}): {} a {} — {} pregoes com arquivo, {} cotacoes gravadas", reason, from, to,
                    found, total);
        }
        return new DaysResult(from, to, days, total, isins);
    }

    /** Ultimo dia gravado pelo COTAHIST; nulo sem nenhum. */
    public LocalDate lastB3Day() {
        return jdbc.queryForObject(
                "SELECT MAX(reference_date) FROM asset_price_history WHERE source = 'B3_COTAHIST'", LocalDate.class);
    }

    /**
     * Atrasado quando falta o pregao do ultimo dia util anterior a hoje (o de hoje pode ainda nao ter saido).
     * Feriado conta como dia util aqui: a rodada so encontra 404 e nao grava nada.
     */
    public boolean stale() {
        LocalDate last = lastB3Day();
        LocalDate expected = LocalDate.now(ZONE).minusDays(1);
        while (expected.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || expected.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            expected = expected.minusDays(1);
        }
        return last == null || last.isBefore(expected);
    }

    // ------------------------------------------------------------------ leitura de um zip

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** Mapas do catalogo para casar as linhas (ticker e ISIN) e o ISIN mais recente visto por ativo. */
    private record Catalog(Map<String, Long> byTicker, Map<String, Long> byIsin, Map<Long, LatestIsin> latestIsin) {
    }

    private Catalog catalog() {
        Map<String, Long> byTicker = new HashMap<>();
        Map<String, Long> byIsin = new HashMap<>();
        for (Asset a : assetRepository.findAllWithType()) {
            if (TYPES.contains(a.getAssetType().getName())) {
                byTicker.put(a.getTicker().toUpperCase(), a.getId());
                if (a.getIsin() != null && !a.getIsin().isBlank() && !isOddLot(a.getTicker())) {
                    byIsin.putIfAbsent(a.getIsin().trim().toUpperCase(), a.getId());
                }
            }
        }
        return new Catalog(byTicker, byIsin, new HashMap<>());
    }

    /** Grava as linhas de um zip (ano ou pregao). Devolve {cotacoes, das quais pelo ISIN}. */
    private long[] importZip(java.nio.file.Path zip, Catalog c) {
        List<Object[]> batch = new ArrayList<>(BATCH);
        List<Object[]> viaIsin = new ArrayList<>();
        long[] counts = new long[2];
        CotahistQuote.forEach(zip, q -> {
            Long assetId = c.byTicker().get(q.ticker());
            boolean spot = CotahistQuote.MARKET_SPOT.equals(q.market());
            boolean direct = assetId != null;
            if (direct) {
                c.latestIsin().merge(assetId, new LatestIsin(q.date(), q.isin()),
                        (a, b) -> b.date().isAfter(a.date()) ? b : a);
                if (spot && !q.isin().isEmpty() && !isOddLot(q.ticker())) {
                    c.byIsin().putIfAbsent(q.isin(), assetId);
                }
            } else if (spot && !q.isin().isEmpty()) {
                // So a vista: o fracionario (PETR4F) tem o mesmo ISIN do lote padrao
                // e nao pode cair na linha do PETR4.
                assetId = c.byIsin().get(q.isin());
            }
            if (assetId == null || q.close().signum() <= 0) {
                return;
            }
            Object[] row = {assetId, Date.valueOf(q.date()), q.open(), q.high(), q.low(), q.close(), q.quantity()};
            counts[0]++;
            if (!direct) {
                // gravadas depois das do arquivo inteiro, sem sobrescrever: no dia da
                // troca de ticker, vale o pregao do ticker atual
                viaIsin.add(row);
                counts[1]++;
                return;
            }
            batch.add(row);
            if (batch.size() >= BATCH) {
                flush(batch);
            }
        });
        flush(batch);
        if (!viaIsin.isEmpty()) {
            jdbc.batchUpdate(INSERT_IF_ABSENT, viaIsin);
        }
        return counts;
    }

    /** Do primeiro ano da serie ({@value #FIRST_YEAR}) ate o ano corrente. */
    public Result importAll(String reason) {
        return importYears(FIRST_YEAR, LocalDate.now(ZONE).getYear(), reason);
    }

    /**
     * Ticker do fracionario (PETR4F). Ele tem o mesmo ISIN do lote padrao, entao
     * nunca e destino da busca pelo ISIN — senao o pregao do CALI3 cairia no
     * CALI3F quando o catalogo so tem o fracionario (visto na carga de 27/09).
     */
    static boolean isOddLot(String ticker) {
        return ticker != null && ticker.length() > 5 && ticker.endsWith("F");
    }

    private void flush(List<Object[]> batch) {
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(UPSERT, batch);
            batch.clear();
        }
    }

    private int updateIsins(Map<Long, LatestIsin> latest) {
        List<Object[]> args = new ArrayList<>();
        latest.forEach((assetId, l) -> {
            if (!l.isin().isEmpty()) {
                args.add(new Object[]{l.isin(), assetId, l.isin()});
            }
        });
        int changed = 0;
        for (int n : jdbc.batchUpdate(
                "UPDATE assets SET isin = ? WHERE id = ? AND isin IS DISTINCT FROM ?", args)) {
            changed += Math.max(n, 0);
        }
        return changed;
    }

    private record LatestIsin(LocalDate date, String isin) {
    }
}
