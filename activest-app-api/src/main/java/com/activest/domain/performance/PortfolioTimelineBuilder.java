package com.activest.domain.performance;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetType;
import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.corporateaction.AssetStockEvent;
import com.activest.domain.corporateaction.AssetStockEventRepository;
import com.activest.domain.corporateaction.StockEventStep;
import com.activest.domain.dividend.Dividend;
import com.activest.domain.dividend.DividendRepository;
import com.activest.domain.fixedincome.FixedIncomeCurve;
import com.activest.domain.fixedincome.FixedIncomeLots;
import com.activest.domain.fixedincome.FixedIncomeNote;
import com.activest.domain.fixedincome.FixedIncomeTaxCalculator;
import com.activest.domain.fixedincome.FixedIncomeTaxTable;
import com.activest.domain.fixedincome.FixedIncomeValuation;
import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioAssetRepository;
import com.activest.domain.portfolio.PortfolioRepository;
import com.activest.domain.portfolio.PositionCalculator;
import com.activest.domain.transaction.Transaction;
import com.activest.domain.transaction.TransactionRepository;
import com.activest.domain.transaction.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Monta a {@link PortfolioTimeline} do usuario (docs/planos/PLANO_RENTABILIDADE.md, 4.1-4.5) com o que ja existe:
 * transacoes, eventos societarios, proventos, historico de preco (COTAHIST, BRAPI, Tesouro, cripto), a curva da renda
 * fixa ({@link FixedIncomeValuation#path}) e o preco atual no ponto de hoje (REGRAS 2.6).
 *
 * <ul>
 *   <li><b>Valor</b> (R2): cotas ao fim do dia ({@link PositionCalculator#sharesOn}, com desdobramentos) x fechamento
 *   do dia; Tesouro = cotas x PU de venda; renda fixa privada = saldo na curva. Liquido: renda fixa pelos lotes,
 *   Tesouro pelo titulo como um lote (a mesma conta da Carteira).</li>
 *   <li><b>Fluxos</b> (R3): compra = aporte no comeco do dia; venda = retirada no fim do dia (bruto ou liquido).
 *   Transacao em dia sem pregao (cripto no fim de semana) entra no pregao seguinte.</li>
 *   <li><b>Proventos</b> (R4, L3): no pregao ex — o primeiro dia util depois da data-com, quando a cotacao cai. O
 *   provento sai da carteira (nao ha conta de caixa).</li>
 *   <li><b>Preco</b> (R10, D3, revisao de 25/10/2026): do proprio dia; sem negocio, o ultimo fechamento ate 5 pregoes
 *   antes (STALE); alem disso o ultimo fechamento segue carregado (CARRIED); sem nenhum fechamento anterior, o preco
 *   da ultima compra ate o dia (CARRIED); renda fixa sem curva, o valor aplicado (CARRIED). MISSING so quando nao ha
 *   numero nenhum.</li>
 * </ul>
 *
 * <p><b>Cache</b> (4.5, L1): a linha do tempo fica em memoria por usuario e e refeita quando a "impressao digital" dos
 * dados que ela usa muda (transacoes, posicoes, proventos, precos dos ativos da carteira, indices) ou em 6 horas.
 * Sem evento para perder: qualquer escrita, de qualquer caminho, muda a impressao digital.</p>
 */
@Slf4j
@Service
public class PortfolioTimelineBuilder {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Duration TTL = Duration.ofHours(6);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final TransactionRepository transactionRepository;
    private final AssetStockEventRepository stockEventRepository;
    private final DividendRepository dividendRepository;
    private final PositionCalculator positionCalculator;
    private final FixedIncomeValuation fixedIncomeValuation;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    private record Cached(String fingerprint, Instant at, PortfolioTimeline timeline) {
    }

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public PortfolioTimelineBuilder(PortfolioRepository portfolioRepository,
                                    PortfolioAssetRepository portfolioAssetRepository,
                                    TransactionRepository transactionRepository,
                                    AssetStockEventRepository stockEventRepository,
                                    DividendRepository dividendRepository, PositionCalculator positionCalculator,
                                    FixedIncomeValuation fixedIncomeValuation, JdbcTemplate jdbc) {
        this(portfolioRepository, portfolioAssetRepository, transactionRepository, stockEventRepository,
                dividendRepository, positionCalculator, fixedIncomeValuation, jdbc, Clock.system(ZONE));
    }

    PortfolioTimelineBuilder(PortfolioRepository portfolioRepository,
                             PortfolioAssetRepository portfolioAssetRepository,
                             TransactionRepository transactionRepository,
                             AssetStockEventRepository stockEventRepository, DividendRepository dividendRepository,
                             PositionCalculator positionCalculator, FixedIncomeValuation fixedIncomeValuation,
                             JdbcTemplate jdbc, Clock clock) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.transactionRepository = transactionRepository;
        this.stockEventRepository = stockEventRepository;
        this.dividendRepository = dividendRepository;
        this.positionCalculator = positionCalculator;
        this.fixedIncomeValuation = fixedIncomeValuation;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** A linha do tempo do usuario (do cache, se os dados nao mudaram). */
    @Transactional(readOnly = true)
    public PortfolioTimeline timeline(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return PortfolioTimeline.empty(today);
        }
        String fp = fingerprint(portfolio.getId(), today);
        Cached c = cache.get(userId);
        if (c != null && c.fingerprint().equals(fp) && c.at().plus(TTL).isAfter(Instant.now(clock))) {
            return c.timeline();
        }
        long t0 = System.currentTimeMillis();
        PortfolioTimeline timeline = build(portfolio.getId(), today);
        cache.put(userId, new Cached(fp, Instant.now(clock), timeline));
        log.debug("Linha do tempo de {}: {} dias x {} posicoes em {} ms", userId, timeline.dates().size(),
                timeline.positions().size(), System.currentTimeMillis() - t0);
        return timeline;
    }

    /** Esquece o que esta em cache (testes; o uso normal depende so da impressao digital). */
    public void evictAll() {
        cache.clear();
    }

    /**
     * O que, se mudar, muda a linha do tempo. Uma consulta, barata: contagens e maximos indexados.
     */
    String fingerprint(UUID portfolioId, LocalDate today) {
        String sql = """
                SELECT concat_ws('|',
                  (SELECT count(*) || ':' || coalesce(max(t.updated_at)::text, '') || ':' || coalesce(sum(t.total_amount), 0)
                          || ':' || coalesce(sum(t.net_amount), 0)
                     FROM transactions t JOIN portfolio_assets pa ON pa.id = t.portfolio_asset_id
                    WHERE pa.portfolio_id = ?),
                  (SELECT count(*) || ':' || coalesce(sum(CASE WHEN pa.is_reservation THEN 1 ELSE 0 END), 0)
                          || ':' || coalesce(sum(a.current_price), 0)
                     FROM portfolio_assets pa JOIN assets a ON a.id = pa.asset_id WHERE pa.portfolio_id = ?),
                  (SELECT count(*) || ':' || coalesce(sum(d.total_amount), 0) || ':' || coalesce(sum(d.net_amount), 0)
                          || ':' || coalesce(max(d.ex_date)::text, '')
                     FROM dividends d JOIN portfolio_assets pa ON pa.id = d.portfolio_asset_id
                    WHERE pa.portfolio_id = ?),
                  (SELECT count(*) || ':' || coalesce(max(h.reference_date)::text, '')
                     FROM asset_price_history h
                    WHERE h.asset_id IN (SELECT pa.asset_id FROM portfolio_assets pa WHERE pa.portfolio_id = ?)),
                  (SELECT count(*) || ':' || coalesce(max(v.updated_at)::text, '') FROM market_index_values v),
                  (SELECT count(*) FROM stock_event_costs c JOIN portfolio_assets pa ON pa.id = c.portfolio_asset_id
                    WHERE pa.portfolio_id = ?),
                  ?::text)
                """;
        return jdbc.queryForObject(sql, String.class, portfolioId, portfolioId, portfolioId, portfolioId,
                portfolioId, today.toString());
    }

    // ------------------------------------------------------------------ montagem

    PortfolioTimeline build(UUID portfolioId, LocalDate today) {
        List<PortfolioAsset> positions = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolioId);
        Map<UUID, List<Transaction>> txByPosition = transactionRepository
                .findByPortfolioAssetPortfolioIdOrderByTransactionDateDesc(portfolioId).stream()
                .filter(tx -> !tx.getTransactionDate().isAfter(today))
                .collect(Collectors.groupingBy(tx -> tx.getPortfolioAsset().getId()));
        positions = positions.stream().filter(pa -> txByPosition.containsKey(pa.getId())).toList();
        if (positions.isEmpty()) {
            return PortfolioTimeline.empty(today);
        }

        LocalDate start = txByPosition.values().stream().flatMap(List::stream)
                .map(Transaction::getTransactionDate).min(LocalDate::compareTo).orElse(today);
        List<LocalDate> dates = axis(start, today, tradingDays(start));
        PortfolioTimeline shell = new PortfolioTimeline(dates, List.of(), today);

        Set<Long> assetIds = positions.stream().map(pa -> pa.getAsset().getId()).collect(Collectors.toSet());
        Map<Long, List<StockEventStep>> steps = new HashMap<>();
        for (AssetStockEvent e : stockEventRepository.findByAssetIds(assetIds)) {
            steps.computeIfAbsent(e.getAsset().getId(), k -> new ArrayList<>()).add(e.toStep(null));
        }
        Map<UUID, List<Dividend>> divByPosition = dividendRepository.findByPortfolioIdWithAsset(portfolioId).stream()
                .collect(Collectors.groupingBy(d -> d.getPortfolioAsset().getId()));

        Map<Long, Long> priceAsset = priceAssets(positions);
        Map<Long, NavigableMap<LocalDate, Double>> prices = prices(new HashSet<>(priceAsset.values()),
                dates.get(0).minusDays(40));

        FixedIncomeValuation.Series series = null;
        List<PortfolioTimeline.PositionSeries> out = new ArrayList<>();
        for (PortfolioAsset pa : positions) {
            List<Transaction> chron = PositionCalculator.chronological(txByPosition.get(pa.getId()));
            AssetType type = pa.getAsset().getAssetType();
            try {
                if (FixedIncomeValuation.usesCurve(pa)) {
                    if (series == null) {
                        series = fixedIncomeValuation.series();
                    }
                    out.add(fixedIncome(shell, pa, chron, divByPosition.get(pa.getId()), series));
                } else if (type != null && type.isValuedByTotal()) {
                    out.add(noCurve(shell, pa, chron, divByPosition.get(pa.getId())));
                } else {
                    if (type != null && type.isTreasury() && series == null) {
                        series = fixedIncomeValuation.series();
                    }
                    out.add(byShares(shell, pa, chron, steps.getOrDefault(pa.getAsset().getId(), List.of()),
                            divByPosition.get(pa.getId()), prices.get(priceAsset.get(pa.getAsset().getId())),
                            type != null && type.isTreasury() ? series.taxes() : null));
                }
            } catch (RuntimeException e) {
                log.error("Linha do tempo: posicao {} ({}) sem serie: {}", pa.getId(), pa.getAsset().getTicker(),
                        e.getMessage(), e);
                out.add(noCurve(shell, pa, chron, divByPosition.get(pa.getId())));
            }
        }
        return new PortfolioTimeline(dates, out, today);
    }

    /**
     * Pregoes de {@code start} a hoje, mais hoje (o ponto do preco atual). Pregao = dia util em que a B3 abriu: o
     * calendario bancario tem 24/12 e 31/12, e a B3 nao — sem esse corte, todo ativo apareceria "sem negocio" nesses
     * dias. A B3 aberta se reconhece pelo fechamento do Ibovespa; depois do ultimo publicado, vale o dia util.
     */
    static List<LocalDate> axis(LocalDate start, LocalDate today, java.util.NavigableSet<LocalDate> trading) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate lastTrading = trading.isEmpty() ? null : trading.last();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            boolean open = BusinessCalendar.isBusinessDay(d)
                    && (lastTrading == null || d.isAfter(lastTrading) || trading.contains(d));
            if (open || d.equals(today)) {
                dates.add(d);
            }
        }
        if (dates.isEmpty()) {
            dates.add(today);
        }
        return dates;
    }

    /** Dias com fechamento do Ibovespa desde {@code start} (o calendario de pregoes da B3). */
    private java.util.NavigableSet<LocalDate> tradingDays(LocalDate start) {
        java.util.TreeSet<LocalDate> out = new java.util.TreeSet<>();
        jdbc.query("SELECT v.reference_date FROM market_index_values v JOIN market_indices i ON i.id = v.index_id "
                        + "WHERE i.code = 'IBOV' AND v.reference_date >= ?",
                rs -> { out.add(rs.getDate(1).toLocalDate()); }, java.sql.Date.valueOf(start));
        return out;
    }

    // ------------------------------------------------------------------ por quantidade (RV, cripto, Tesouro)

    private PortfolioTimeline.PositionSeries byShares(PortfolioTimeline shell, PortfolioAsset pa,
                                                      List<Transaction> chron, List<StockEventStep> steps,
                                                      List<Dividend> dividends,
                                                      NavigableMap<LocalDate, Double> closes,
                                                      FixedIncomeTaxTable treasuryTaxes) {
        Arrays a = new Arrays(shell.dates().size());
        flows(shell, chron, a);
        List<LocalDate> dates = shell.dates();
        LocalDate today = shell.today();
        Asset asset = pa.getAsset();
        double current = asset.getCurrentPrice() != null ? asset.getCurrentPrice().doubleValue() : 0;
        boolean treasury = treasuryTaxes != null;
        // Tesouro: o titulo como um lote (PositionValuationInfo.treasuryLot)
        BigDecimal buyPrice = null;
        LocalDate purchase = pa.getNote() != null ? pa.getNote().getPurchaseDate() : null;
        for (Transaction tx : chron) {
            if (tx.getType() == TransactionType.BUY) {
                if (buyPrice == null) {
                    buyPrice = tx.getPricePerShare();
                }
                if (purchase == null) {
                    purchase = tx.getTransactionDate();
                }
            }
        }
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = dates.get(i);
            double shares = positionCalculator.sharesOn(chron, steps, d).doubleValue();
            if (shares <= 0) {
                continue;
            }
            a.held[i] = true;
            double price;
            if (d.equals(today) && current > 0) {
                price = current;
                a.status[i] = PriceStatus.OK;
                a.priceDate[i] = d;
            } else {
                Map.Entry<LocalDate, Double> e = closes != null ? closes.floorEntry(d) : null;
                if (e == null) {
                    e = lastBuyPrice(chron, d);
                    if (e == null) {
                        a.status[i] = PriceStatus.MISSING;
                        continue;
                    }
                    a.status[i] = PriceStatus.CARRIED;
                } else if (e.getKey().equals(d)) {
                    a.status[i] = PriceStatus.OK;
                } else if (staleDays(dates, e.getKey(), i) <= PortfolioTimeline.MAX_STALE_DAYS) {
                    a.status[i] = PriceStatus.STALE;
                } else {
                    a.status[i] = PriceStatus.CARRIED;
                }
                price = e.getValue();
                a.priceDate[i] = e.getKey();
            }
            // centavo por posicao, como a Carteira (valorizacao com 2 casas): o ultimo ponto bate com o total dela
            double gross = BigDecimal.valueOf(shares).multiply(BigDecimal.valueOf(price))
                    .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
            a.gross[i] = gross;
            a.net[i] = gross;
            if (treasury && buyPrice != null && purchase != null) {
                BigDecimal g = BigDecimal.valueOf(gross).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal applied = BigDecimal.valueOf(shares).multiply(buyPrice)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                FixedIncomeTaxTable.Rates rates = treasuryTaxes.rates(asset.getId(), purchase, d);
                a.net[i] = g.subtract(FixedIncomeTaxCalculator.taxes(g.subtract(applied), rates).total()).doubleValue();
            }
        }
        List<PortfolioTimeline.Distribution> dists = distributions(shell, dividends, a);
        return series(pa, a, dists, treasury || hasWithholding(dists));
    }

    /** Sem nenhum fechamento ate o dia: o preco da ultima compra ate ele (data da compra, preco por cota). */
    private static Map.Entry<LocalDate, Double> lastBuyPrice(List<Transaction> chron, LocalDate d) {
        Map.Entry<LocalDate, Double> out = null;
        for (Transaction tx : chron) {
            if (tx.getTransactionDate().isAfter(d)) {
                break;
            }
            if (tx.getType() == TransactionType.BUY && tx.getPricePerShare() != null
                    && tx.getPricePerShare().signum() > 0) {
                out = Map.entry(tx.getTransactionDate(), tx.getPricePerShare().doubleValue());
            }
        }
        return out;
    }

    /** Pregoes do eixo depois da data do preco ate o dia {@code i}. */
    static int staleDays(List<LocalDate> dates, LocalDate priceDate, int i) {
        int j = java.util.Collections.binarySearch(dates, priceDate);
        int firstAfter = j >= 0 ? j + 1 : -j - 1;
        return i - firstAfter + 1;
    }

    // ------------------------------------------------------------------ renda fixa privada

    private PortfolioTimeline.PositionSeries fixedIncome(PortfolioTimeline shell, PortfolioAsset pa,
                                                         List<Transaction> chron, List<Dividend> dividends,
                                                         FixedIncomeValuation.Series series) {
        Arrays a = new Arrays(shell.dates().size());
        flows(shell, chron, a);
        List<LocalDate> dates = shell.dates();
        LocalDate first = chron.get(0).getTransactionDate();
        FixedIncomeLots.Path path = fixedIncomeValuation.path(pa, chron, first, shell.today(), series);
        boolean unsupported = path == null || path.status() == FixedIncomeCurve.Status.UNSUPPORTED;
        for (int i = 0; i < dates.size(); i++) {
            FixedIncomeLots.DayValue v = path != null ? path.days().get(dates.get(i)) : null;
            if (v == null || v.gross().signum() <= 0) {
                continue;
            }
            a.held[i] = true;
            a.gross[i] = v.gross().doubleValue();
            a.net[i] = v.net().doubleValue();
            a.priceDate[i] = dates.get(i);
            // sem curva: o valor aplicado, parado (como a Carteira)
            a.status[i] = unsupported ? PriceStatus.CARRIED
                    : v.estimated() ? PriceStatus.ESTIMATED : PriceStatus.OK;
        }
        List<PortfolioTimeline.Distribution> dists = distributions(shell, dividends, a);
        return series(pa, a, dists, true);
    }

    /** Renda fixa sem titulo (dado antigo) ou que falhou: o saldo e o valor aplicado em aberto, carregado (CARRIED). */
    private PortfolioTimeline.PositionSeries noCurve(PortfolioTimeline shell, PortfolioAsset pa,
                                                     List<Transaction> chron, List<Dividend> dividends) {
        Arrays a = new Arrays(shell.dates().size());
        flows(shell, chron, a);
        boolean byTotal = pa.getAsset().getAssetType() != null && pa.getAsset().getAssetType().isValuedByTotal();
        double open = 0;
        for (int i = 0; i < shell.dates().size(); i++) {
            open += a.buy[i] - a.sellGross[i];
            if (byTotal ? open > 0.005 : positionCalculator.sharesOn(chron, shell.dates().get(i)).signum() > 0) {
                a.held[i] = true;
                a.gross[i] = Math.max(0, open);
                a.net[i] = a.gross[i];
                a.status[i] = PriceStatus.CARRIED;
            }
        }
        List<PortfolioTimeline.Distribution> dists = distributions(shell, dividends, a);
        return series(pa, a, dists, byTotal);
    }

    // ------------------------------------------------------------------ fluxos e proventos

    private static void flows(PortfolioTimeline shell, List<Transaction> chron, Arrays a) {
        for (Transaction tx : chron) {
            int i = shell.indexOnOrAfter(tx.getTransactionDate());
            if (i >= a.size) {
                continue;
            }
            double total = amount(tx);
            if (tx.getType() == TransactionType.BUY) {
                a.buy[i] += total;
            } else if (tx.getType() == TransactionType.SELL) {
                a.sellGross[i] += total;
                a.sellNet[i] += tx.getNetAmount() != null ? tx.getNetAmount().doubleValue() : total;
            }
        }
    }

    private static double amount(Transaction tx) {
        if (tx.getTotalAmount() != null) {
            return tx.getTotalAmount().doubleValue();
        }
        if (tx.getShares() != null && tx.getPricePerShare() != null) {
            return tx.getShares().multiply(tx.getPricePerShare()).doubleValue();
        }
        return 0;
    }

    /** Proventos no pregao ex (o primeiro dia util depois da data-com), ate hoje. */
    private static List<PortfolioTimeline.Distribution> distributions(PortfolioTimeline shell, List<Dividend> dividends,
                                                                      Arrays a) {
        List<PortfolioTimeline.Distribution> out = new ArrayList<>();
        if (dividends == null) {
            return out;
        }
        for (Dividend d : dividends) {
            if (d.getExDate() == null || d.getTotalAmount() == null || d.getTotalAmount().signum() <= 0) {
                continue;
            }
            LocalDate ex = d.getExDate().plusDays(1);
            while (!BusinessCalendar.isBusinessDay(ex)) {
                ex = ex.plusDays(1);
            }
            if (ex.isAfter(shell.today())) {
                continue;
            }
            int i = shell.indexOnOrAfter(ex);
            if (i >= a.size) {
                continue;
            }
            double gross = d.getTotalAmount().doubleValue();
            double net = d.getNetAmount() != null ? d.getNetAmount().doubleValue() : gross;
            a.distGross[i] += gross;
            a.distNet[i] += net;
            out.add(new PortfolioTimeline.Distribution(i, ex, d.getPaymentDate(), gross, net));
        }
        return out;
    }

    private static boolean hasWithholding(List<PortfolioTimeline.Distribution> dists) {
        return dists.stream().anyMatch(d -> Math.abs(d.gross() - d.net()) > 0.004);
    }

    private static PortfolioTimeline.PositionSeries series(PortfolioAsset pa, Arrays a,
                                                           List<PortfolioTimeline.Distribution> dists,
                                                           boolean basisSensitive) {
        Asset asset = pa.getAsset();
        AssetType type = asset.getAssetType();
        FixedIncomeNote note = pa.getNote();
        String issuerGroup = note != null && note.getIssuer() != null ? note.getIssuer().getDisplayName() : null;
        String noteLabel = null;
        if (note != null && (type != null && type.isValuedByTotal())) {
            noteLabel = (note.getDistributor() != null ? note.getDistributor().getDisplayName() : "")
                    + (note.getMaturityDate() != null ? " · venc. " + note.getMaturityDate() : "");
        } else if (note != null && type != null && type.isTreasury() && note.getPurchaseDate() != null) {
            noteLabel = "aplicação " + note.getPurchaseDate();
        }
        return new PortfolioTimeline.PositionSeries(pa.getId(), asset.getId(), asset.getTicker(), asset.getName(),
                type != null ? type.getName() : null, type != null ? type.getDescription() : null,
                asset.getAssetCategory() != null ? asset.getAssetCategory().getId() : null,
                asset.getAssetCategory() != null ? asset.getAssetCategory().getDisplayName() : null,
                issuerGroup, noteLabel, pa.isReservation(), basisSensitive,
                a.gross, a.net, a.buy, a.sellGross, a.sellNet, a.distGross, a.distNet, a.held, a.status,
                a.priceDate, List.copyOf(dists));
    }

    // ------------------------------------------------------------------ precos

    /** Ativo de onde vem o preco de cada ativo: o fracionario (PETR4F) usa o lote padrao, se existir. */
    private Map<Long, Long> priceAssets(List<PortfolioAsset> positions) {
        Map<Long, Long> out = new HashMap<>();
        Map<String, Long> wanted = new HashMap<>();
        for (PortfolioAsset pa : positions) {
            Asset a = pa.getAsset();
            out.put(a.getId(), a.getId());
            String base = baseTicker(a.getTicker());
            if (!base.equals(a.getTicker().toUpperCase())) {
                wanted.put(base, a.getId());
            }
        }
        if (!wanted.isEmpty()) {
            String in = wanted.keySet().stream().map(t -> "'" + t.replace("'", "") + "'")
                    .collect(Collectors.joining(","));
            jdbc.query("SELECT id, upper(ticker) AS t FROM assets WHERE upper(ticker) IN (" + in + ")",
                    rs -> { out.put(wanted.get(rs.getString("t")), rs.getLong("id")); });
        }
        return out;
    }

    static String baseTicker(String ticker) {
        String t = ticker.trim().toUpperCase();
        return t.endsWith("F") && t.length() > 5 && Character.isDigit(t.charAt(t.length() - 2))
                ? t.substring(0, t.length() - 1) : t;
    }

    private Map<Long, NavigableMap<LocalDate, Double>> prices(Collection<Long> assetIds, LocalDate from) {
        Map<Long, NavigableMap<LocalDate, Double>> out = new HashMap<>();
        if (assetIds.isEmpty()) {
            return out;
        }
        String in = assetIds.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
        jdbc.query("SELECT asset_id, reference_date, close_price FROM asset_price_history WHERE asset_id IN (" + in
                        + ") AND reference_date >= ? AND close_price > 0",
                rs -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new TreeMap<>())
                            .put(rs.getDate(2).toLocalDate(), rs.getBigDecimal(3).doubleValue());
                }, java.sql.Date.valueOf(from));
        return out;
    }

    /** Os vetores de uma posicao, do tamanho do eixo. */
    private static final class Arrays {
        final int size;
        final double[] gross;
        final double[] net;
        final double[] buy;
        final double[] sellGross;
        final double[] sellNet;
        final double[] distGross;
        final double[] distNet;
        final boolean[] held;
        final PriceStatus[] status;
        final LocalDate[] priceDate;

        Arrays(int n) {
            size = n;
            gross = new double[n];
            net = new double[n];
            buy = new double[n];
            sellGross = new double[n];
            sellNet = new double[n];
            distGross = new double[n];
            distNet = new double[n];
            held = new boolean[n];
            status = new PriceStatus[n];
            priceDate = new LocalDate[n];
        }
    }
}
