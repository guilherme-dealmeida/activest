package com.primevest.adm.domain.pricehistory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.adm.integration.binance.BinanceKlinesClient;
import com.primevest.adm.integration.coingecko.CoinGeckoClient;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.catalog.CatalogMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Historico de preco de cripto (docs/planos/PLANO_RENTABILIDADE.md, 3.3; D2; resolve a PENDENCIAS 20.3 no que a fonte
 * permite). A Rentabilidade precisa do preco de cada dia em que a moeda esteve na carteira, e o fechamento diario
 * ({@code ClosingPriceService}) so grava a partir do dia em que comecou a rodar.
 *
 * <ul>
 *   <li><b>Quem:</b> toda cripto que esta ou ja esteve em alguma carteira, mais a que e benchmark ({@code benchmarks}).</li>
 *   <li><b>CoinGecko</b>, ultimos 365 dias (o limite da API gratuita): o preco de cada dia de Sao Paulo e o ultimo
 *   ponto do dia. O que vem e somado ao arquivo {@code carga/data/coingecko/historico/{id}.json} — o arquivo guarda
 *   os dias que ja sairam da janela, e um ambiente novo le dele.</li>
 *   <li><b>Binance</b> (klines publico): antes da janela, so para os pares de {@link #BINANCE_PAIRS} (BTC/BRL desde
 *   13/10/2020). Nenhuma serie e reconstruida (nada de BTCUSDT x PTAX).</li>
 *   <li>Grava com {@code ON CONFLICT DO NOTHING}: o fechamento diario e a CoinGecko nunca sao sobrescritos. So dias
 *   anteriores a hoje (hoje e do fechamento das 19:00).</li>
 * </ul>
 *
 * <p>Idempotente. Avisa a carteira ("catalogo mudou", FECHAMENTO) dos ativos que ganharam linha.</p>
 */
@Slf4j
@Service
public class CryptoHistoryService {

    /** Ticker do catalogo → par diario da Binance em reais. */
    static final Map<String, String> BINANCE_PAIRS = Map.of("BTC", "BTCBRL");
    /** Primeiro ano com candle do BTCBRL na Binance (13/10/2020). */
    static final int BINANCE_FIRST_YEAR = 2020;
    /** A CoinGecko gratuita recusa (401) qualquer instante antes de 365 dias; 364 deixa folga. */
    static final int COINGECKO_WINDOW_DAYS = 364;

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INSERT = """
            INSERT INTO asset_price_history
                (asset_id, reference_date, open_price, high_price, low_price, close_price, source, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (asset_id, reference_date) DO NOTHING
            """;

    @Autowired(required = false)
    CatalogMessaging.CatalogChangePublisher catalogChangePublisher;

    private final JdbcTemplate jdbc;
    private final CoinGeckoClient coinGecko;
    private final BinanceKlinesClient binance;
    private final Path dir;
    private final Clock clock;

    @Autowired
    public CryptoHistoryService(JdbcTemplate jdbc, CoinGeckoClient coinGecko, BinanceKlinesClient binance,
                                @Value("${primevest.data.dir:../carga/data}") String dataDir) {
        this(jdbc, coinGecko, binance, Path.of(dataDir, "coingecko", "historico"), Clock.system(ZONE));
    }

    CryptoHistoryService(JdbcTemplate jdbc, CoinGeckoClient coinGecko, BinanceKlinesClient binance, Path dir,
                         Clock clock) {
        this.jdbc = jdbc;
        this.coinGecko = coinGecko;
        this.binance = binance;
        this.dir = dir.toAbsolutePath().normalize();
        this.clock = clock;
    }

    /** Uma moeda: ticker, linhas gravadas por fonte, e o erro (se houve). */
    public record CoinResult(String ticker, int coinGecko, int binance, String error) {
    }

    public record Result(List<CoinResult> coins, int rows) {
    }

    record Coin(long assetId, String ticker, String coinId) {
    }

    /** Todas as moedas que a Rentabilidade usa. */
    public synchronized Result importAll(String reason) {
        List<Coin> coins = jdbc.query("""
                SELECT a.id, a.ticker, a.price_source_ref FROM assets a
                JOIN asset_types t ON t.id = a.asset_type_id
                WHERE t.price_source = 'COINGECKO'
                  AND (EXISTS (SELECT 1 FROM portfolio_assets pa WHERE pa.asset_id = a.id)
                       OR EXISTS (SELECT 1 FROM benchmarks b WHERE b.asset_id = a.id AND b.active))
                ORDER BY a.ticker
                """, (rs, i) -> new Coin(rs.getLong(1), rs.getString(2), rs.getString(3)));
        return run(coins, reason);
    }

    /** Uma moeda (transacao nova de cripto, pedido "preciso do historico"). */
    public synchronized Result importAsset(long assetId, String reason) {
        List<Coin> coins = jdbc.query("""
                SELECT a.id, a.ticker, a.price_source_ref FROM assets a
                JOIN asset_types t ON t.id = a.asset_type_id
                WHERE a.id = ? AND t.price_source = 'COINGECKO'
                """, (rs, i) -> new Coin(rs.getLong(1), rs.getString(2), rs.getString(3)), assetId);
        return run(coins, reason);
    }

    private Result run(List<Coin> coins, String reason) {
        List<CoinResult> results = new ArrayList<>();
        Set<Long> changed = new HashSet<>();
        int total = 0;
        LocalDate today = LocalDate.now(clock);
        for (Coin c : coins) {
            int cg = 0;
            int bn = 0;
            List<String> errors = new ArrayList<>();
            if (c.coinId() == null || c.coinId().isBlank()) {
                errors.add("sem id da CoinGecko (price_source_ref)");
            } else {
                try {
                    cg = insert(c.assetId(), coinGeckoDays(c.coinId(), today), "COINGECKO");
                } catch (RuntimeException e) {
                    errors.add("CoinGecko: " + e.getMessage());
                }
            }
            String pair = BINANCE_PAIRS.get(c.ticker().toUpperCase());
            if (pair != null) {
                try {
                    bn = insertCandles(c.assetId(), pair, today);
                } catch (RuntimeException e) {
                    errors.add("Binance: " + e.getMessage());
                }
            }
            if (cg + bn > 0) {
                changed.add(c.assetId());
            }
            total += cg + bn;
            String error = errors.isEmpty() ? null : String.join("; ", errors);
            if (error != null) {
                log.warn("Historico de cripto ({}): {} — {}", reason, c.ticker(), error);
            }
            results.add(new CoinResult(c.ticker(), cg, bn, error));
        }
        if (!changed.isEmpty() && catalogChangePublisher != null) {
            try {
                catalogChangePublisher.publish(new CatalogChange(CatalogChange.Origin.FECHAMENTO, changed));
            } catch (RuntimeException e) {
                log.warn("Historico de cripto: aviso a carteira nao publicado: {}", e.getMessage());
            }
        }
        log.info("Historico de cripto ({}): {} moedas, {} dias gravados {}", reason, coins.size(), total, results);
        return new Result(results, total);
    }

    // ------------------------------------------------------------------ CoinGecko + arquivo

    /**
     * Dias da moeda: o arquivo somado ao que a CoinGecko devolve na janela (o novo vence no mesmo dia). Grava o
     * arquivo antes de devolver. Sem rede, fica so com o arquivo.
     */
    TreeMap<LocalDate, BigDecimal> coinGeckoDays(String coinId, LocalDate today) {
        Path file = dir.resolve(coinId + ".json");
        TreeMap<LocalDate, BigDecimal> days = readFile(file);
        Instant to = Instant.now(clock);
        Instant from = to.minus(Duration.ofDays(COINGECKO_WINDOW_DAYS));
        List<CoinGeckoClient.HistoryPoint> points;
        try {
            points = coinGecko.fetchHistoryBrl(coinId, from, to);
        } catch (RuntimeException e) {
            if (days.isEmpty()) {
                throw e;
            }
            log.warn("Historico de cripto: CoinGecko fora ({}); usando {} dias do arquivo", e.getMessage(), days.size());
            return before(days, today);
        }
        // Ultimo ponto de cada dia de Sao Paulo (o diario das 00:00 UTC e 21:00 do dia anterior aqui).
        TreeMap<Instant, CoinGeckoClient.HistoryPoint> ordered = new TreeMap<>();
        points.forEach(p -> ordered.put(p.at(), p));
        for (CoinGeckoClient.HistoryPoint p : ordered.values()) {
            LocalDate d = p.at().atZone(ZONE).toLocalDate();
            if (d.isBefore(today) && p.price().signum() > 0) {
                days.put(d, p.price());
            }
        }
        writeFile(file, days);
        return before(days, today);
    }

    private static TreeMap<LocalDate, BigDecimal> before(TreeMap<LocalDate, BigDecimal> days, LocalDate today) {
        return new TreeMap<>(days.headMap(today, false));
    }

    static TreeMap<LocalDate, BigDecimal> readFile(Path file) {
        TreeMap<LocalDate, BigDecimal> days = new TreeMap<>();
        if (!Files.isRegularFile(file)) {
            return days;
        }
        try {
            Map<String, String> raw = JSON.readValue(file.toFile(), new TypeReference<Map<String, String>>() {});
            raw.forEach((k, v) -> days.put(LocalDate.parse(k), new BigDecimal(v)));
        } catch (IOException | RuntimeException e) {
            log.warn("Historico de cripto: arquivo {} ilegivel ({}); ignorado", file, e.getMessage());
        }
        return days;
    }

    private static void writeFile(Path file, TreeMap<LocalDate, BigDecimal> days) {
        try {
            Files.createDirectories(file.getParent());
            Map<String, String> raw = new java.util.LinkedHashMap<>();
            days.forEach((d, v) -> raw.put(d.toString(), v.toPlainString()));
            Path tmp = file.resolveSibling(file.getFileName() + ".part");
            JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), raw);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Historico de cripto: arquivo {} nao gravado: {}", file, e.getMessage());
        }
    }

    private int insert(long assetId, TreeMap<LocalDate, BigDecimal> days, String source) {
        List<Object[]> rows = new ArrayList<>();
        days.forEach((d, price) -> rows.add(new Object[]{assetId, Date.valueOf(d), null, null, null, price, source}));
        return sum(jdbc.batchUpdate(INSERT, rows));
    }

    // ------------------------------------------------------------------ Binance

    private int insertCandles(long assetId, String pair, LocalDate today) {
        List<Object[]> rows = new ArrayList<>();
        for (int year = BINANCE_FIRST_YEAR; year <= today.getYear(); year++) {
            for (BinanceKlinesClient.DailyCandle k : binance.year(pair, year)) {
                if (k.date().isBefore(today) && k.close().signum() > 0) {
                    rows.add(new Object[]{assetId, Date.valueOf(k.date()), k.open(), k.high(), k.low(), k.close(),
                            "BINANCE"});
                }
            }
        }
        return sum(jdbc.batchUpdate(INSERT, rows));
    }

    private static int sum(int[] counts) {
        int n = 0;
        for (int c : counts) {
            n += Math.max(c, 0);
        }
        return n;
    }
}
