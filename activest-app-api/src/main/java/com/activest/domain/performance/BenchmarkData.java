package com.activest.domain.performance;

import com.activest.domain.benchmark.Benchmark;
import com.activest.domain.benchmark.BenchmarkKind;
import com.activest.domain.benchmark.BenchmarkRepository;
import com.activest.domain.marketindex.IndexUnit;
import com.activest.domain.marketindex.MarketIndex;
import com.activest.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Os benchmarks da Rentabilidade (docs/planos/PLANO_RENTABILIDADE.md, 3.1 e R9): o catalogo ({@code benchmarks}, V57)
 * e a serie diaria de cada um no eixo da carteira. A serie vem de onde ja esta — {@code market_index_values} ou
 * {@code asset_price_history} — e nada e copiado.
 */
@Service
public class BenchmarkData {

    /**
     * D4 (17/10/2026): benchmarks padrao ao filtrar uma classe, o primeiro e o principal; o que nao esta aqui usa o
     * CDI. O Tesouro (ajuste de 17/10/2026) e IPCA + IPCA + a taxa do Tesouro IPCA+ a venda mais curto
     * ({@link #treasuryIpca()}); sem essa taxa, so o IPCA.
     */
    public static final Map<String, List<String>> DEFAULTS_BY_TYPE = Map.of(
            "ACAO", List.of("IBOV"),
            "FII", List.of("IFIX"),
            "RENDA_FIXA", List.of("CDI"),
            "TESOURO_DIRETO", List.of("IPCA"),
            "CRIPTO", List.of("BTC"),
            "BDR", List.of("SP500_IVVB11"));
    public static final String DEFAULT_CODE = "CDI";
    static final String TREASURY_TYPE = "TESOURO_DIRETO";

    private final BenchmarkRepository repository;
    private final JdbcTemplate jdbc;

    public BenchmarkData(BenchmarkRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /** Um benchmark pedido: codigo e, no IPCA + x%, o x (% a.a.). */
    public record Ref(String code, Double spread) {

        /** "CDI", "IPCA_MAIS:6" ou "IPCA_MAIS:6.5". */
        public static Ref parse(String raw) {
            String s = raw.trim().toUpperCase(Locale.ROOT);
            int colon = s.indexOf(':');
            if (colon < 0) {
                return new Ref(s, null);
            }
            try {
                return new Ref(s.substring(0, colon), Double.parseDouble(s.substring(colon + 1).replace(',', '.')));
            } catch (NumberFormatException e) {
                throw new BusinessException("Taxa inválida no benchmark: " + raw);
            }
        }

        /** Chave estavel na resposta: "IPCA_MAIS:6.0". */
        public String key() {
            return spread == null ? code : code + ":" + spread;
        }
    }

    /** Benchmark resolvido: nome para a tela e a serie no eixo. */
    public record Resolved(Ref ref, String name, BenchmarkSeries.Daily daily) {
    }

    @Transactional(readOnly = true)
    public PerformanceDTOs.BenchmarkCatalogDTO catalog() {
        List<PerformanceDTOs.BenchmarkDTO> out = new ArrayList<>();
        for (Benchmark b : repository.findActive()) {
            LocalDate[] range = range(b);
            String source;
            String periodicity;
            if (b.getKind() == BenchmarkKind.INDICE) {
                MarketIndex i = b.getMarketIndex();
                source = i.getSource().name();
                periodicity = i.getPeriodicity().name();
            } else {
                source = b.getAsset().getAssetType() != null ? b.getAsset().getAssetType().getName() : "ATIVO";
                periodicity = "DIARIA";
            }
            out.add(new PerformanceDTOs.BenchmarkDTO(b.getCode(), b.getName(), b.getCategory().name(),
                    b.getKind().name(), b.getNote(), b.isAcceptsSpread(), b.isDefaultSelected(), source, periodicity,
                    range[0], range[1]));
        }
        PerformanceDTOs.TreasuryReferenceDTO treasury = treasuryIpca();
        Map<String, List<String>> defaults = new LinkedHashMap<>(DEFAULTS_BY_TYPE);
        if (treasury != null) {
            defaults.put(TREASURY_TYPE, List.of("IPCA", "IPCA_MAIS:" + treasury.rate().stripTrailingZeros().toPlainString()));
        }
        return new PerformanceDTOs.BenchmarkCatalogDTO(out, defaults, treasury);
    }

    /**
     * O Tesouro IPCA+ (sem juros semestrais) a venda que vence primeiro, com a taxa de compra do ultimo dia do arquivo
     * do Tesouro ({@code treasury_offers}, V59). Nulo se nao ha nenhum (carga do Tesouro ainda nao rodou).
     */
    @Transactional(readOnly = true)
    public PerformanceDTOs.TreasuryReferenceDTO treasuryIpca() {
        List<PerformanceDTOs.TreasuryReferenceDTO> list = jdbc.query("""
                SELECT a.ticker, t.maturity_date, o.buy_rate, o.rate_date
                FROM treasury_offers o
                JOIN assets a ON a.id = o.asset_id
                JOIN asset_terms t ON t.asset_id = a.id
                WHERE o.on_sale AND o.buy_rate IS NOT NULL AND a.ticker ~ '^Tesouro IPCA\\+ [0-9]{4}$'
                  AND t.maturity_date > CURRENT_DATE
                ORDER BY t.maturity_date
                LIMIT 1""",
                (rs, i) -> new PerformanceDTOs.TreasuryReferenceDTO(rs.getString(1), rs.getDate(2).toLocalDate(),
                        rs.getBigDecimal(3), rs.getDate(4).toLocalDate()));
        return list.isEmpty() ? null : list.get(0);
    }

    private LocalDate[] range(Benchmark b) {
        String sql = b.getKind() == BenchmarkKind.INDICE
                ? "SELECT min(reference_date), max(reference_date) FROM market_index_values WHERE index_id = ?"
                : "SELECT min(reference_date), max(reference_date) FROM asset_price_history WHERE asset_id = ? "
                + "AND close_price > 0";
        Long id = b.getKind() == BenchmarkKind.INDICE ? b.getMarketIndex().getId() : b.getAsset().getId();
        return jdbc.query(sql, rs -> {
            rs.next();
            java.sql.Date a = rs.getDate(1);
            java.sql.Date z = rs.getDate(2);
            return new LocalDate[]{a != null ? a.toLocalDate() : null, z != null ? z.toLocalDate() : null};
        }, id);
    }

    /** Os benchmarks pedidos no eixo; codigo desconhecido ou spread onde nao cabe: 422. */
    @Transactional(readOnly = true)
    public Map<String, Resolved> resolve(List<Ref> refs, List<LocalDate> axis, LocalDate today) {
        Map<String, Resolved> out = new LinkedHashMap<>();
        if (axis.isEmpty()) {
            return out;
        }
        Map<String, Benchmark> byCode = new LinkedHashMap<>();
        repository.findActive().forEach(b -> byCode.put(b.getCode(), b));
        for (Ref ref : refs) {
            Benchmark b = byCode.get(ref.code());
            if (b == null) {
                throw new BusinessException("Benchmark desconhecido: " + ref.code());
            }
            if (ref.spread() != null && !b.isAcceptsSpread()) {
                throw new BusinessException("O benchmark " + b.getName() + " não aceita taxa adicional");
            }
            if (b.isAcceptsSpread() && ref.spread() == null) {
                throw new BusinessException("Informe a taxa do " + b.getName() + " (ex.: IPCA_MAIS:6)");
            }
            if (ref.spread() != null && (ref.spread() < -10 || ref.spread() > 50)) {
                throw new BusinessException("Taxa fora do intervalo aceito (-10% a 50% a.a.)");
            }
            String name = b.isAcceptsSpread()
                    ? b.getName().replace("x%", format(ref.spread()) + "%") : b.getName();
            out.put(ref.key(), new Resolved(ref, name, daily(b, ref.spread(), axis, today)));
        }
        return out;
    }

    /** Serie de um indice pelo codigo (CDI e IPCA, sempre usados por % do CDI, real e risco). */
    @Transactional(readOnly = true)
    public BenchmarkSeries.Daily byCode(String code, List<LocalDate> axis, LocalDate today) {
        Benchmark b = repository.findByCode(code).orElse(null);
        if (b == null || axis.isEmpty()) {
            double[] r = new double[axis.size()];
            java.util.Arrays.fill(r, Double.NaN);
            return new BenchmarkSeries.Daily(r, null);
        }
        return daily(b, null, axis, today);
    }

    private BenchmarkSeries.Daily daily(Benchmark b, Double spread, List<LocalDate> axis, LocalDate today) {
        LocalDate from = axis.get(0).minusDays(45);
        LocalDate to = axis.get(axis.size() - 1);
        if (b.getKind() == BenchmarkKind.INDICE) {
            MarketIndex index = b.getMarketIndex();
            NavigableMap<LocalDate, BigDecimal> values = new TreeMap<>();
            jdbc.query("SELECT reference_date, value FROM market_index_values WHERE index_id = ? "
                            + "AND reference_date BETWEEN ? AND ?",
                    rs -> { values.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)); },
                    index.getId(), java.sql.Date.valueOf(from.withDayOfMonth(1)), java.sql.Date.valueOf(to));
            IndexUnit unit = index.getUnit();
            if (unit == IndexUnit.PCT_DIA) {
                return BenchmarkSeries.dailyRate(axis, values);
            }
            if (unit == IndexUnit.PCT_MES) {
                return BenchmarkSeries.monthlyRate(axis, values, spread != null ? spread : 0);
            }
            NavigableMap<LocalDate, Double> levels = new TreeMap<>();
            values.forEach((d, v) -> levels.put(d, v.doubleValue()));
            return BenchmarkSeries.level(axis, levels, today, null);
        }
        NavigableMap<LocalDate, Double> levels = new TreeMap<>();
        jdbc.query("SELECT reference_date, close_price FROM asset_price_history WHERE asset_id = ? "
                        + "AND reference_date BETWEEN ? AND ? AND close_price > 0",
                rs -> { levels.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2).doubleValue()); },
                b.getAsset().getId(), java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
        BigDecimal current = b.getAsset().getCurrentPrice();
        return BenchmarkSeries.level(axis, levels, today, current != null ? current.doubleValue() : null);
    }

    private static String format(Double v) {
        if (v == null) {
            return "x";
        }
        String s = new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(
                Locale.forLanguageTag("pt-BR"))).format(v);
        return s;
    }
}
