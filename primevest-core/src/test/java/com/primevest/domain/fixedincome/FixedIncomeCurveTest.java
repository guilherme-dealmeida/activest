package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.calendar.BusinessCalendar;
import com.primevest.domain.fixedincome.FixedIncomeCurve.Flow;
import com.primevest.domain.fixedincome.FixedIncomeCurve.Result;
import com.primevest.domain.fixedincome.FixedIncomeCurve.Status;
import com.primevest.domain.fixedincome.FixedIncomeCurve.Terms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** R3 do docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md: a curva da renda fixa privada, com contas feitas a mao. */
class FixedIncomeCurveTest {

    private static final LocalDate JAN2 = LocalDate.of(2024, 1, 2);
    private static final LocalDate JAN31 = LocalDate.of(2024, 1, 31);
    private static final BigDecimal MIL = new BigDecimal("1000");

    /** CDI constante de 0,05% a.d. em todos os dias uteis de 2024 a 2025. */
    private static NavigableMap<LocalDate, BigDecimal> cdi() {
        NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
        for (LocalDate d = JAN2; d.getYear() < 2026; d = d.plusDays(1)) {
            if (BusinessCalendar.isBusinessDay(d)) {
                m.put(d, new BigDecimal("0.05"));
            }
        }
        return m;
    }

    private static final String[] IPCA_2024 = {"0.42", "0.83", "0.16", "0.38", "0.46", "0.21", "0.38", "-0.02",
            "0.44", "0.56", "0.39", "0.52"};

    private static NavigableMap<LocalDate, BigDecimal> ipca2024() {
        NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
        for (int i = 0; i < 12; i++) {
            m.put(LocalDate.of(2024, i + 1, 1), new BigDecimal(IPCA_2024[i]));
        }
        return m;
    }

    private static Result value(RateType rt, Indexer idx, String rate, LocalDate maturity, LocalDate ref, Flow... flows) {
        return FixedIncomeCurve.value(new Terms(rt, idx, new BigDecimal(rate), maturity), List.of(flows), ref, cdi(),
                ipca2024());
    }

    private static Flow compra(LocalDate d, String v) {
        return new Flow(d, new BigDecimal(v));
    }

    private static Flow resgate(LocalDate d, String v) {
        return new Flow(d, new BigDecimal(v).negate());
    }

    @Test
    @DisplayName("Prefixado 12% a.a. por exatos 252 dias uteis rende 12%")
    void prefixado() {
        LocalDate d = JAN2;
        int n = 0;
        LocalDate last = d;
        while (n < 252) {
            if (BusinessCalendar.isBusinessDay(d)) {
                n++;
                last = d;
            }
            d = d.plusDays(1);
        }
        Result r = value(RateType.PREFIXADO, null, "12", null, last, compra(JAN2, "1000"));

        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.balance()).isEqualByComparingTo("1120.00");
    }

    @Test
    @DisplayName("100% e 110% do CDI e CDI + 2% a.a. nos 22 dias uteis de janeiro/2024")
    void porCdi() {
        assertThat(value(RateType.POS_FIXADO, Indexer.CDI, "100", null, JAN31, compra(JAN2, "1000")).balance().doubleValue())
                .isCloseTo(1000 * Math.pow(1.0005, 22), within(0.005));
        assertThat(value(RateType.POS_FIXADO, Indexer.CDI, "110", null, JAN31, compra(JAN2, "1000")).balance().doubleValue())
                .isCloseTo(1000 * Math.pow(1.00055, 22), within(0.005));
        assertThat(value(RateType.HIBRIDO, Indexer.CDI, "2", null, JAN31, compra(JAN2, "1000")).balance().doubleValue())
                .isCloseTo(1000 * Math.pow(1.0005 * Math.pow(1.02, 1 / 252.0), 22), within(0.005));
    }

    @Test
    @DisplayName("Aporte e resgate no meio: cada trecho rende so a partir da sua data")
    void aportesResgates() {
        LocalDate jan16 = LocalDate.of(2024, 1, 16); // 11o dia util
        Result r = value(RateType.POS_FIXADO, Indexer.CDI, "100", null, JAN31,
                compra(JAN2, "1000"), compra(jan16, "500"), resgate(LocalDate.of(2024, 1, 23), "300"));

        double f = 1.0005;
        // 2..15: 10 dias; 16..22: 5 dias; 23..31: 7 dias
        double esperado = ((1000 * Math.pow(f, 10) + 500) * Math.pow(f, 5) - 300) * Math.pow(f, 7);
        assertThat(r.balance().doubleValue()).isCloseTo(esperado, within(0.005));
    }

    @Test
    @DisplayName("Resgate acima do saldo (D4): o saldo fica no centavo da regra F e segue rendendo")
    void resgateAcima() {
        Result r = value(RateType.POS_FIXADO, Indexer.CDI, "100", null, JAN2,
                compra(JAN2, "1000"), resgate(JAN2, "2000"));

        assertThat(r.balance()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("Vencido (D8): para de render na vespera do vencimento e sai como MATURED")
    void vencido() {
        LocalDate venc = LocalDate.of(2024, 1, 15);
        Result r = value(RateType.POS_FIXADO, Indexer.CDI, "100", venc, JAN31, compra(JAN2, "1000"));

        assertThat(r.status()).isEqualTo(Status.MATURED);
        assertThat(r.referenceDate()).isEqualTo(venc);
        // 2..12 de janeiro: 9 dias uteis
        assertThat(r.balance().doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 9), within(0.005));
    }

    @Test
    @DisplayName("IPCA + 6% em 2024 inteiro = IPCA de 2024 (4,83%) x 1,06^(253/252)")
    void ipcaMaisSpread() {
        Result r = value(RateType.HIBRIDO, Indexer.IPCA, "6", null, LocalDate.of(2024, 12, 31),
                compra(LocalDate.of(2024, 1, 1), "1000"));

        assertThat(r.estimated()).isFalse();
        assertThat(r.balance().doubleValue())
                .isCloseTo(1000 * 1.0483129579 * Math.pow(1.06, 253 / 252.0), within(0.01));
    }

    @Test
    @DisplayName("Mes ainda nao divulgado usa o ultimo IPCA publicado (D5) e sai como estimado")
    void estimado() {
        Result r = value(RateType.HIBRIDO, Indexer.IPCA, "0", null, LocalDate.of(2025, 1, 31),
                compra(LocalDate.of(2024, 12, 1), "1000"));

        assertThat(r.estimated()).isTrue();
        assertThat(r.balance().doubleValue()).isCloseTo(1000 * 1.0052 * 1.0052, within(0.01));
    }

    @Test
    @DisplayName("Sem curva: TR, sem taxa, sem serie, aplicacao antes da serie")
    void semCurva() {
        assertThat(value(RateType.HIBRIDO, Indexer.TR, "1", null, JAN31, compra(JAN2, "1000")).status())
                .isEqualTo(Status.UNSUPPORTED);
        assertThat(FixedIncomeCurve.value(new Terms(RateType.PREFIXADO, null, null, null), List.of(), JAN31, null, null)
                .reason()).isEqualTo("sem taxa cadastrada");
        assertThat(FixedIncomeCurve.value(new Terms(RateType.POS_FIXADO, Indexer.CDI, MIL, null), List.of(), JAN31,
                new TreeMap<>(), null).status()).isEqualTo(Status.UNSUPPORTED);
        assertThat(value(RateType.POS_FIXADO, Indexer.CDI, "100", null, JAN31, compra(LocalDate.of(2023, 6, 1), "1000"))
                .reason()).contains("anterior");
    }
}
