package com.activest.domain.goal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Formulas das projecoes das metas (docs/planos/PLANO_METAS.md, Anexo A 6.2), com as bordas do cenario 16. */
class GoalProjectionTest {

    @Test
    @DisplayName("Taxa mensal equivalente: 12,6825% a.a. = 1% a.m.")
    void monthlyRate() {
        assertThat(GoalProjection.monthlyRate(Math.pow(1.01, 12) - 1)).isCloseTo(0.01, within(1e-12));
    }

    @Test
    @DisplayName("Valor no prazo: R$ 10.000 + R$ 1.000/mês a 1% a.m. por 12 meses = R$ 23.950,75 (conta à mão)")
    void futureValue() {
        // 10.000 × 1,01^12 = 11.268,25; 1.000 × (1,01^12 − 1)/0,01 = 12.682,50
        assertThat(GoalProjection.futureValue(10_000, 1_000, 0.01, 12)).isCloseTo(23_950.75, within(0.01));
        assertThat(GoalProjection.futureValue(10_000, 1_000, 0, 12)).isEqualTo(22_000);
        assertThat(GoalProjection.futureValue(10_000, 1_000, 0.01, 0)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("Aporte necessário: o inverso do valor no prazo; ≤ 0 vira zero; prazo vencido não se aplica")
    void requiredPayment() {
        double fv = GoalProjection.futureValue(10_000, 1_000, 0.01, 12);
        assertThat(GoalProjection.requiredPayment(fv, 10_000, 0.01, 12)).isCloseTo(1_000, within(1e-6));
        assertThat(GoalProjection.requiredPayment(22_000, 10_000, 0, 12)).isCloseTo(1_000, within(1e-9));
        assertThat(GoalProjection.requiredPayment(5_000, 10_000, 0.01, 12)).isZero();
        assertThat(GoalProjection.requiredPayment(50_000, 10_000, 0.01, 0)).isNull();
    }

    @Test
    @DisplayName("Meses até o alvo: o inverso; já atingido = 0; sem aporte e sem rendimento não alcança")
    void monthsToTarget() {
        double fv = GoalProjection.futureValue(10_000, 1_000, 0.01, 24);
        assertThat(GoalProjection.monthsToTarget(fv, 10_000, 1_000, 0.01)).isCloseTo(24, within(1e-6));
        assertThat(GoalProjection.monthsToTarget(22_000, 10_000, 1_000, 0)).isCloseTo(12, within(1e-9));
        assertThat(GoalProjection.monthsToTarget(9_000, 10_000, 0, 0)).isZero();
        assertThat(GoalProjection.monthsToTarget(50_000, 10_000, 0, 0)).isNull();
        assertThat(GoalProjection.monthsToTarget(50_000, 10_000, -100, 0)).isNull();
        // mais de 100 anos
        assertThat(GoalProjection.monthsToTarget(1e12, 1, 1, 0.0001)).isNull();
    }

    @Test
    @DisplayName("Valor real: nominal ÷ (1 + inflação)^(n/12)")
    void real() {
        assertThat(GoalProjection.real(110, 0.10, 12)).isCloseTo(100, within(1e-9));
        assertThat(GoalProjection.real(100, 0, 60)).isEqualTo(100);
    }
}
