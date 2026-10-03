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

        Map<Long, LatestIsin> latestIsin = new HashMap<>();
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
            List<Object[]> batch = new ArrayList<>(BATCH);
            List<Object[]> viaIsin = new ArrayList<>();
            long[] counts = new long[2];
            CotahistQuote.forEach(zip.get(), q -> {
                Long assetId = byTicker.get(q.ticker());
                boolean spot = CotahistQuote.MARKET_SPOT.equals(q.market());
                boolean direct = assetId != null;
                if (direct) {
                    latestIsin.merge(assetId, new LatestIsin(q.date(), q.isin()),
                            (a, b) -> b.date().isAfter(a.date()) ? b : a);
                    if (spot && !q.isin().isEmpty() && !isOddLot(q.ticker())) {
                        byIsin.putIfAbsent(q.isin(), assetId);
                    }
                } else if (spot && !q.isin().isEmpty()) {
                    // So a vista: o fracionario (PETR4F) tem o mesmo ISIN do lote padrao
                    // e nao pode cair na linha do PETR4.
                    assetId = byIsin.get(q.isin());
                }
                if (assetId == null || q.close().signum() <= 0) {
                    return;
                }
                Object[] row = {assetId, Date.valueOf(q.date()), q.open(), q.high(), q.low(), q.close(), q.quantity()};
                counts[0]++;
                if (!direct) {
                    // gravadas depois das do ano inteiro, sem sobrescrever: no dia da
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
            total += counts[0];
            years.add(new YearResult(year, true, counts[0], counts[1]));
            log.info("Serie historica B3: {} — {} pregoes gravados ({} pelo ISIN)", year, counts[0], counts[1]);
        }

        int isins = updateIsins(latestIsin);
        Result r = new Result(years, total, isins);
        log.info("Serie historica B3 ({}) concluida: {} pregoes, {} ISINs gravados no catalogo", reason, total, isins);
        return r;
    }

    /** Do primeiro ano da serie ({@value #FIRST_YEAR}) ate o ano corrente. */
    public Result importAll(String reason) {
        return importYears(FIRST_YEAR, LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear(), reason);
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
