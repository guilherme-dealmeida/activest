package com.activest.domain.marketindex;

import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.marketindex.YieldCurve.Bond;
import com.activest.domain.marketindex.YieldCurve.Curve;
import com.activest.domain.marketindex.YieldCurve.Vertex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** docs/planos/PLANO_JUROS_FUTUROS.md, 5.1: pontos do dia, ancora no CDI, flat-forward 252, sem extrapolacao. */
class YieldCurveTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private static Bond bond(String type, LocalDate maturity, String buy, String sell) {
        return new Bond(type, maturity, buy == null ? null : new BigDecimal(buy), sell == null ? null : new BigDecimal(sell));
    }

    @Test
    @DisplayName("Dias uteis pela tabela acumulada batem com o BusinessCalendar (data inclusive, vencimento exclusive)")
    void businessDays() {
        LocalDate[] ends = {LocalDate.of(2027, 1, 1), LocalDate.of(2031, 1, 1), LocalDate.of(2060, 8, 15)};
        for (LocalDate end : ends) {
            assertThat(YieldCurve.businessDays(DAY, end))
                    .isEqualTo(BusinessCalendar.businessDaysBetween(DAY, end.minusDays(1)));
        }
        assertThat(YieldCurve.businessDays(LocalDate.of(1999, 12, 1), LocalDate.of(2000, 2, 1)))
                .isEqualTo(BusinessCalendar.businessDaysBetween(LocalDate.of(1999, 12, 1), LocalDate.of(2000, 1, 31)));
        assertThat(YieldCurve.businessDays(DAY, DAY)).isZero();
    }

    @Test
    @DisplayName("Media de compra e venda; faltando uma, a que existe (P1); sem cupom prevalece no mesmo vencimento")
    void pointsFromBonds() {
        LocalDate m = LocalDate.of(2029, 1, 1);
        List<Bond> bonds = List.of(
                bond("Tesouro Prefixado com Juros Semestrais", m, "14.49", "14.61"),
                bond("Tesouro Prefixado", m, "14.59", "14.71"),
                bond("Tesouro Prefixado", LocalDate.of(2031, 1, 1), null, "15.08"),
                bond("Tesouro IPCA+", LocalDate.of(2035, 5, 15), "7.71", "7.83"),
                bond("Tesouro Prefixado", LocalDate.of(2026, 1, 1), "10", "10")); // vencido
        NavigableMap<Integer, BigDecimal> pts = YieldCurve.points(Curve.PRE, DAY, bonds, null);
        assertThat(pts).hasSize(2);
        assertThat(pts.get(YieldCurve.businessDays(DAY, m))).isEqualByComparingTo("14.65");
        assertThat(pts.get(YieldCurve.businessDays(DAY, LocalDate.of(2031, 1, 1)))).isEqualByComparingTo("15.08");
        assertThat(YieldCurve.points(Curve.REAL, DAY, bonds, null)).hasSize(1);
    }

    @Test
    @DisplayName("Flat-forward 252: no ponto devolve a taxa, entre pontos compoe os fatores, fora dos pontos nada")
    void flatForward() {
        NavigableMap<Integer, BigDecimal> pts = new java.util.TreeMap<>();
        pts.put(252, new BigDecimal("10"));
        pts.put(756, new BigDecimal("12"));
        assertThat(YieldCurve.rate(pts, 252)).contains(new BigDecimal("10.00000000"));
        double fa = 1.10;
        double fb = Math.pow(1.12, 3);
        double expected = (Math.pow(fa * Math.pow(fb / fa, 0.5), 252.0 / 504) - 1) * 100;
        assertThat(YieldCurve.rate(pts, 504)).get().satisfies(r -> assertThat(r.doubleValue()).isCloseTo(expected, within(1e-6)));
        assertThat(YieldCurve.rate(pts, 251)).isEmpty();
        assertThat(YieldCurve.rate(pts, 757)).isEmpty();
        assertThat(YieldCurve.rate(new java.util.TreeMap<>(), 252)).isEmpty();
    }

    @Test
    @DisplayName("Prefixada ancora o curto no CDI do dia (I4); a real nunca tem ancora")
    void anchor() {
        List<Bond> bonds = List.of(bond("Tesouro Prefixado", LocalDate.of(2028, 1, 1), "14.08", "14.20"),
                bond("Tesouro IPCA+", LocalDate.of(2029, 5, 15), "7.51", "7.63"));
        BigDecimal cdi = YieldCurve.annualize(new BigDecimal("0.055131"));
        assertThat(cdi.doubleValue()).isCloseTo(14.90, within(0.01));
        Vertex pre1 = Vertex.parse("PRE:1");
        assertThat(YieldCurve.rate(pre1, DAY, bonds, null)).isEmpty(); // primeiro titulo vence depois de 1 ano
        assertThat(YieldCurve.rate(pre1, DAY, bonds, cdi)).get()
                .satisfies(r -> assertThat(r.doubleValue()).isBetween(14.14, 14.90));
        assertThat(YieldCurve.points(Curve.REAL, DAY, bonds, cdi)).doesNotContainKey(1);
    }

    @Test
    @DisplayName("source_ref no formato CURVA:ANOS")
    void parse() {
        assertThat(Vertex.parse("REAL:20")).isEqualTo(new Vertex(Curve.REAL, 20));
        assertThat(Vertex.parse("pre:5").businessDays()).isEqualTo(1260);
        assertThatThrownBy(() -> Vertex.parse("PRE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Vertex.parse("PRE:0")).isInstanceOf(IllegalArgumentException.class);
    }
}
