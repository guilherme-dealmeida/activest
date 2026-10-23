package com.activest.domain.performance;

import java.time.LocalDate;
import java.util.List;

/**
 * Indicadores de risco (docs/planos/PLANO_RENTABILIDADE.md, R14). Regra pura sobre os retornos diarios do recorte e do
 * CDI nos mesmos dias.
 *
 * <ul>
 *   <li>Volatilidade anualizada: desvio-padrao amostral dos retornos diarios x raiz(252).</li>
 *   <li>Maximo drawdown: maior queda do indice acumulado desde um pico (com as datas de pico e vale).</li>
 *   <li>Sharpe: (retorno anualizado - CDI anualizado) / volatilidade.</li>
 *   <li>Sortino: o mesmo excesso / desvio dos dias abaixo do CDI ({@code raiz(soma(min(0, r - cdi)^2) / n)} x raiz(252)).</li>
 * </ul>
 */
public final class RiskMetrics {

    private RiskMetrics() {
    }

    public record Result(Double volatility, Double maxDrawdown, LocalDate peak, LocalDate trough, Double sharpe,
                         Double sortino, Double annualReturn, Double annualCdi, int days) {
    }

    /**
     * @param r     retornos diarios do recorte (sem NaN) nos dias {@code dates}
     * @param cdi   CDI de cada um desses dias (NaN = sem CDI: o dia fica fora do excesso)
     */
    public static Result of(double[] r, double[] cdi, List<LocalDate> dates) {
        int n = r.length;
        if (n < 2) {
            return new Result(null, null, null, null, null, null, null, null, n);
        }
        double mean = 0;
        for (double v : r) {
            mean += v;
        }
        mean /= n;
        double var = 0;
        for (double v : r) {
            var += (v - mean) * (v - mean);
        }
        double vol = Math.sqrt(var / (n - 1)) * Math.sqrt(252);

        double idx = 1;
        double peakIdx = 1;
        LocalDate peakDate = dates.get(0);
        double mdd = 0;
        LocalDate mddPeak = null;
        LocalDate mddTrough = null;
        double cdiF = 1;
        double down = 0;
        int cdiDays = 0;
        for (int i = 0; i < n; i++) {
            idx *= 1 + r[i];
            if (idx > peakIdx) {
                peakIdx = idx;
                peakDate = dates.get(i);
            }
            double dd = idx / peakIdx - 1;
            if (dd < mdd) {
                mdd = dd;
                mddPeak = peakDate;
                mddTrough = dates.get(i);
            }
            if (!Double.isNaN(cdi[i])) {
                cdiF *= 1 + cdi[i];
                double ex = r[i] - cdi[i];
                if (ex < 0) {
                    down += ex * ex;
                }
                cdiDays++;
            }
        }
        Double annual = ReturnMath.annualize(idx - 1, n);
        Double annualCdi = cdiDays > 0 ? ReturnMath.annualize(cdiF - 1, cdiDays) : null;
        Double sharpe = annual != null && annualCdi != null && vol > 0 ? (annual - annualCdi) / vol : null;
        double dd = cdiDays > 0 ? Math.sqrt(down / cdiDays) * Math.sqrt(252) : 0;
        Double sortino = annual != null && annualCdi != null && dd > 0 ? (annual - annualCdi) / dd : null;
        return new Result(vol, mdd, mddPeak, mddTrough, sharpe, sortino, annual, annualCdi, n);
    }
}
