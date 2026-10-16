package com.primevest.domain.fixedincome;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * O jeito de calcular o IR e o IOF de um lote — o que nao muda com a lei (docs/planos/PLANO_VENDA_RENDA_FIXA.md,
 * R6 e D3). As aliquotas vem do {@link FixedIncomeTaxTable}.
 *
 * <ol>
 *   <li>IOF = rendimento x % do dia, <b>truncado</b> nos centavos;</li>
 *   <li>IR = (rendimento - IOF) x aliquota do prazo, <b>truncado</b> nos centavos;</li>
 *   <li>rendimento zero ou negativo: nenhum imposto.</li>
 * </ol>
 *
 * <p>Truncar e a mesma convencao do IR dos proventos (V23). O banco pode arredondar diferente: a diferenca de
 * centavos e aceita (R12).</p>
 */
public final class FixedIncomeTaxCalculator {

    private FixedIncomeTaxCalculator() {
    }

    /** IR e IOF de um lote (ou de uma parte dele). */
    public record Taxes(BigDecimal iof, BigDecimal ir) {

        public static final Taxes NONE = new Taxes(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));

        public BigDecimal total() {
            return iof.add(ir);
        }

        public Taxes plus(Taxes other) {
            return new Taxes(iof.add(other.iof), ir.add(other.ir));
        }
    }

    public static Taxes taxes(BigDecimal income, FixedIncomeTaxTable.Rates rates) {
        if (income == null || income.signum() <= 0) {
            return Taxes.NONE;
        }
        BigDecimal iof = income.multiply(rates.iof()).setScale(2, RoundingMode.DOWN);
        BigDecimal ir = income.subtract(iof).multiply(rates.ir()).setScale(2, RoundingMode.DOWN);
        return new Taxes(iof, ir);
    }
}
