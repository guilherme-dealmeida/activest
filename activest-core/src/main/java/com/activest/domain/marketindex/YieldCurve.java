package com.activest.domain.marketindex;

import com.activest.domain.calendar.BusinessCalendar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Curva de juros do Tesouro por prazo fixo (docs/planos/PLANO_JUROS_FUTUROS.md, secao 5.1). Regra pura: quem le o
 * arquivo do Tesouro e grava a serie e a adm-api.
 *
 * <ol>
 *   <li>Titulos do dia: prefixada = Tesouro Prefixado + Tesouro Prefixado com Juros Semestrais; real = Tesouro IPCA+ +
 *   Tesouro IPCA+ com Juros Semestrais (I1: os com cupom entram como aproximacao). Mesmo vencimento nos dois: vale o
 *   sem cupom.</li>
 *   <li>Taxa do titulo = media das taxas de compra e de venda (D5); faltando uma, a que existe (P1).</li>
 *   <li>Prazo em dias uteis (base 252) da data (inclusive) ao vencimento (exclusive), pelo {@link BusinessCalendar}.</li>
 *   <li>Prefixada: ponto de 1 dia util = CDI do dia anualizado (I4).</li>
 *   <li>Interpolacao flat-forward 252 entre os dois pontos que cercam o prazo (D6); fora dos pontos, sem valor
 *   (nunca extrapola).</li>
 * </ol>
 */
public final class YieldCurve {

    /** Dias uteis por ano: prazo do vertice = anos x 252. */
    public static final int DAYS_PER_YEAR = 252;
    private static final int SCALE = 8;
    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);
    /** Por ano: dias uteis acumulados antes de cada dia do ano (indice = dia do ano − 1). */
    private static final Map<Integer, int[]> CUMULATIVE = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> YEAR_START = new ConcurrentHashMap<>();

    public enum Curve {
        /** Juros nominais: Tesouro Prefixado (LTN) e Prefixado com Juros Semestrais (NTN-F). */
        PRE("Tesouro Prefixado", "Tesouro Prefixado com Juros Semestrais"),
        /** Juros reais: Tesouro IPCA+ (NTN-B Principal) e IPCA+ com Juros Semestrais (NTN-B). */
        REAL("Tesouro IPCA+", "Tesouro IPCA+ com Juros Semestrais");

        private final String zeroCoupon;
        private final String coupon;

        Curve(String zeroCoupon, String coupon) {
            this.zeroCoupon = zeroCoupon;
            this.coupon = coupon;
        }

        /** O tipo do arquivo do Tesouro entra nesta curva? */
        public boolean includes(String treasuryType) {
            return zeroCoupon.equals(treasuryType) || coupon.equals(treasuryType);
        }

        boolean isZeroCoupon(String treasuryType) {
            return zeroCoupon.equals(treasuryType);
        }
    }

    /** Um titulo num dia: tipo do arquivo do Tesouro, vencimento e taxas (% a.a.; nulas quando o arquivo traz zero). */
    public record Bond(String type, LocalDate maturity, BigDecimal buyRate, BigDecimal sellRate) {

        /** Media de compra e venda; faltando uma, a outra; sem nenhuma, nula. */
        BigDecimal rate() {
            if (buyRate != null && sellRate != null) {
                return buyRate.add(sellRate).divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_EVEN);
            }
            return buyRate != null ? buyRate : sellRate;
        }
    }

    /** Um prazo da curva, lido do {@code source_ref} ({@code PRE:5}, {@code REAL:10}). */
    public record Vertex(Curve curve, int years) {

        public static Vertex parse(String sourceRef) {
            String[] p = sourceRef == null ? new String[0] : sourceRef.trim().split(":");
            if (p.length != 2) {
                throw new IllegalArgumentException("Prazo da curva invalido: " + sourceRef + " (esperado CURVA:ANOS)");
            }
            int years = Integer.parseInt(p[1].trim());
            if (years <= 0) {
                throw new IllegalArgumentException("Prazo da curva invalido: " + sourceRef);
            }
            return new Vertex(Curve.valueOf(p[0].trim().toUpperCase()), years);
        }

        public int businessDays() {
            return years * DAYS_PER_YEAR;
        }
    }

    private YieldCurve() {
    }

    /** CDI diario em % (0,055131) levado a % a.a. em 252 dias uteis. */
    public static BigDecimal annualize(BigDecimal dailyPct) {
        double f = Math.pow(1 + dailyPct.doubleValue() / 100, DAYS_PER_YEAR);
        return BigDecimal.valueOf((f - 1) * 100).setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    /**
     * Pontos da curva no dia: prazo em dias uteis → taxa % a.a. Titulo vencido ou sem taxa fica de fora.
     *
     * @param cdiAnnualPct CDI do dia em % a.a. (so na prefixada); nulo = sem ancora
     */
    public static NavigableMap<Integer, BigDecimal> points(Curve curve, LocalDate date, Collection<Bond> bonds,
                                                           BigDecimal cdiAnnualPct) {
        NavigableMap<Integer, BigDecimal> out = new TreeMap<>();
        Map<Integer, Boolean> zero = new HashMap<>();
        for (Bond b : bonds) {
            if (!curve.includes(b.type())) {
                continue;
            }
            BigDecimal rate = b.rate();
            int du = businessDays(date, b.maturity());
            if (rate == null || du <= 0) {
                continue;
            }
            boolean isZero = curve.isZeroCoupon(b.type());
            // mesmo prazo: o sem cupom prevalece
            if (out.containsKey(du) && (zero.get(du) || !isZero)) {
                continue;
            }
            out.put(du, rate);
            zero.put(du, isZero);
        }
        if (curve == Curve.PRE && cdiAnnualPct != null && !out.containsKey(1)) {
            out.put(1, cdiAnnualPct);
        }
        return out;
    }

    /** Taxa % a.a. no prazo {@code du} (dias uteis) por flat-forward 252; vazio fora dos pontos. */
    public static Optional<BigDecimal> rate(NavigableMap<Integer, BigDecimal> points, int du) {
        if (points.isEmpty() || du < points.firstKey() || du > points.lastKey()) {
            return Optional.empty();
        }
        BigDecimal exact = points.get(du);
        if (exact != null) {
            return Optional.of(exact.setScale(SCALE, RoundingMode.HALF_EVEN));
        }
        Map.Entry<Integer, BigDecimal> a = points.lowerEntry(du);
        Map.Entry<Integer, BigDecimal> b = points.higherEntry(du);
        double fa = factor(a.getValue(), a.getKey());
        double fb = factor(b.getValue(), b.getKey());
        double w = (double) (du - a.getKey()) / (b.getKey() - a.getKey());
        double f = fa * Math.pow(fb / fa, w);
        double r = (Math.pow(f, (double) DAYS_PER_YEAR / du) - 1) * 100;
        return Optional.of(BigDecimal.valueOf(r).setScale(SCALE, RoundingMode.HALF_EVEN));
    }

    /** Taxa do prazo no dia, direto dos titulos. */
    public static Optional<BigDecimal> rate(Vertex vertex, LocalDate date, Collection<Bond> bonds, BigDecimal cdiAnnualPct) {
        return rate(points(vertex.curve(), date, bonds, cdiAnnualPct), vertex.businessDays());
    }

    private static double factor(BigDecimal ratePct, int du) {
        return Math.pow(1 + ratePct.doubleValue() / 100, (double) du / DAYS_PER_YEAR);
    }

    /** Dias uteis de {@code from} (inclusive) a {@code to} (exclusive); negativo se {@code to} vem antes. */
    public static int businessDays(LocalDate from, LocalDate to) {
        return ordinal(to) - ordinal(from);
    }

    /** Dias uteis antes de {@code d}, contados desde 01/01/2000 (tabela por ano, calculada uma vez). */
    static int ordinal(LocalDate d) {
        if (d.isBefore(EPOCH)) {
            return -BusinessCalendar.businessDaysBetween(d, EPOCH.minusDays(1));
        }
        int[] cum = CUMULATIVE.computeIfAbsent(d.getYear(), YieldCurve::cumulative);
        return yearStart(d.getYear()) + cum[d.getDayOfYear() - 1];
    }

    private static int yearStart(int year) {
        Integer cached = YEAR_START.get(year);
        if (cached != null) {
            return cached;
        }
        int v = year == EPOCH.getYear() ? 0 : yearStart(year - 1) + yearTotal(year - 1);
        YEAR_START.put(year, v);
        return v;
    }

    private static int yearTotal(int year) {
        int[] cum = CUMULATIVE.computeIfAbsent(year, YieldCurve::cumulative);
        return cum[cum.length - 1];
    }

    /** cum[i] = dias uteis antes do dia i+1 do ano; ultima posicao extra = total do ano. */
    private static int[] cumulative(int year) {
        LocalDate first = LocalDate.of(year, 1, 1);
        int len = first.lengthOfYear();
        int[] cum = new int[len + 1];
        int n = 0;
        for (int i = 0; i < len; i++) {
            cum[i] = n;
            if (BusinessCalendar.isBusinessDay(first.plusDays(i))) {
                n++;
            }
        }
        cum[len] = n;
        return cum;
    }
}
