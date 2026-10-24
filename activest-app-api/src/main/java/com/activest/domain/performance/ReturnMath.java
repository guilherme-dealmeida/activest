package com.activest.domain.performance;

/**
 * Contas de retorno (docs/planos/PLANO_RENTABILIDADE.md, R5-R8). Regra pura, sem banco.
 *
 * <p><b>TWR diario</b>: compras no comeco do dia, vendas e proventos no fim —
 * {@code r = (V_t + W_t + D_t) / (V_{t-1} + A_t) - 1}. Assim o primeiro dia mede a compra contra o fechamento e a
 * venda total mede o fechamento da vespera contra o preco de venda. Aporte e retirada nunca viram rendimento
 * (criterio 3). Acumulado por composicao (criterio 5). {@code NaN} = dia indisponivel (R10).</p>
 */
public final class ReturnMath {

    private ReturnMath() {
    }

    /** Retorno de cada dia do eixo; {@code NaN} no indisponivel; 0 sem capital. */
    public static double[] daily(ScopeSeries s) {
        double[] r = new double[s.size()];
        for (int i = 0; i < r.length; i++) {
            if (s.unavailable[i]) {
                r[i] = Double.NaN;
            } else if (s.active(i)) {
                r[i] = (s.value[i] + s.sell[i] + s.dist[i]) / s.denominator(i) - 1;
            }
        }
        return r;
    }

    /** Composicao de {@code r[from..to]}; nulo se algum dia for indisponivel ou o intervalo vazio. */
    public static Double chain(double[] r, int from, int to) {
        if (from < 0 || to < from) {
            return null;
        }
        double f = 1;
        for (int i = from; i <= to; i++) {
            if (Double.isNaN(r[i])) {
                return null;
            }
            f *= 1 + r[i];
        }
        return f - 1;
    }

    /**
     * Composicao ate o primeiro dia indisponivel (para benchmarks que param antes do fim: IPCA nao publicado).
     *
     * @return {acumulado, indice do ultimo dia composto}; acumulado NaN se o primeiro ja faltar
     */
    public static double[] chainUntilGap(double[] r, int from, int to) {
        double f = 1;
        int last = from - 1;
        for (int i = from; i <= to; i++) {
            if (Double.isNaN(r[i])) {
                break;
            }
            f *= 1 + r[i];
            last = i;
        }
        return new double[]{last < from ? Double.NaN : f - 1, last};
    }

    /** Acumulado dia a dia de {@code from} a {@code to}; NaN a partir do primeiro dia indisponivel. */
    public static double[] cumulative(double[] r, int from, int to) {
        double[] out = new double[Math.max(0, to - from + 1)];
        double f = 1;
        boolean broken = false;
        for (int i = from; i <= to; i++) {
            if (broken || Double.isNaN(r[i])) {
                broken = true;
                out[i - from] = Double.NaN;
                continue;
            }
            f *= 1 + r[i];
            out[i - from] = f - 1;
        }
        return out;
    }

    /** R7: {@code (1 + nominal) / (1 + inflacao) - 1}. */
    public static Double real(Double nominal, Double inflation) {
        if (nominal == null || inflation == null) {
            return null;
        }
        return (1 + nominal) / (1 + inflation) - 1;
    }

    /** R8: diferenca em pontos percentuais (fracao: 0,012 = 1,2 p.p.). */
    public static Double diff(Double a, Double b) {
        return a == null || b == null ? null : a - b;
    }

    /** R8 / D5: % do CDI, so com CDI acumulado positivo; carteira negativa da percentual negativo. */
    public static Double percentOfCdi(Double r, Double cdi) {
        if (r == null || cdi == null || cdi <= 1e-9) {
            return null;
        }
        return r / cdi * 100;
    }

    /** Anualiza um acumulado de {@code businessDays} dias uteis (base 252). */
    public static Double annualize(Double total, int businessDays) {
        if (total == null || businessDays <= 0 || total <= -1) {
            return null;
        }
        return Math.pow(1 + total, 252.0 / businessDays) - 1;
    }
}
