package com.activest.domain.goal;

/**
 * Formulas das projecoes das metas (docs/planos/PLANO_METAS.md, Anexo A 6.2): mes a mes, juros compostos, aporte no
 * fim de cada mes. Regra pura, sem banco. {@code i} e a taxa mensal (fracao), {@code n} meses, {@code pv} o valor de
 * hoje, {@code pmt} o aporte mensal, {@code fv} o alvo. Tudo e estimativa (G4): quem chama mostra as premissas.
 */
public final class GoalProjection {

    /** Acima disto (100 anos) a meta "nao alcanca com as premissas atuais". */
    public static final int MAX_MONTHS = 1200;

    private static final double EPS = 1e-12;

    private GoalProjection() {
    }

    /** Taxa mensal equivalente a uma anual: {@code (1 + a)^(1/12) - 1}. */
    public static double monthlyRate(double annual) {
        return Math.pow(1 + annual, 1.0 / 12) - 1;
    }

    /** Valor no prazo: {@code PV(1+i)^n + PMT((1+i)^n - 1)/i}; {@code i = 0} → {@code PV + PMT·n}. */
    public static double futureValue(double pv, double pmt, double i, int n) {
        if (n <= 0) {
            return pv;
        }
        if (Math.abs(i) < EPS) {
            return pv + pmt * n;
        }
        double f = Math.pow(1 + i, n);
        return pv * f + pmt * (f - 1) / i;
    }

    /**
     * Aporte mensal necessario para chegar a {@code fv} em {@code n} meses: {@code (FV - PV(1+i)^n) i / ((1+i)^n - 1)}.
     * Zero quando nao precisa de aporte com estas premissas; nulo com prazo vencido ({@code n <= 0}).
     */
    public static Double requiredPayment(double fv, double pv, double i, int n) {
        if (n <= 0) {
            return null;
        }
        double pmt;
        if (Math.abs(i) < EPS) {
            pmt = (fv - pv) / n;
        } else {
            double f = Math.pow(1 + i, n);
            pmt = (fv - pv * f) * i / (f - 1);
        }
        return Math.max(0, pmt);
    }

    /**
     * Meses ate o alvo: {@code ln((FV·i + PMT)/(PV·i + PMT)) / ln(1+i)}; {@code i = 0} → {@code (FV - PV)/PMT}.
     * Zero se ja chegou; nulo se nao alcanca com as premissas (sem aporte e sem rendimento, ou mais de 100 anos).
     */
    public static Double monthsToTarget(double fv, double pv, double pmt, double i) {
        if (pv >= fv) {
            return 0.0;
        }
        double months;
        if (Math.abs(i) < EPS) {
            if (pmt <= 0) {
                return null;
            }
            months = (fv - pv) / pmt;
        } else {
            double num = fv * i + pmt;
            double den = pv * i + pmt;
            if (den <= 0 || num / den <= 0) {
                return null;
            }
            months = Math.log(num / den) / Math.log(1 + i);
        }
        if (Double.isNaN(months) || months < 0 || months > MAX_MONTHS) {
            return null;
        }
        return months;
    }

    /** Valor real (em reais de hoje): {@code nominal / (1 + inflacao anual)^(n/12)}. */
    public static double real(double nominal, double annualInflation, int n) {
        return nominal / Math.pow(1 + annualInflation, n / 12.0);
    }
}
