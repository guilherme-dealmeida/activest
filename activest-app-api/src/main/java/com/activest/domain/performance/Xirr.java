package com.activest.domain.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Taxa interna de retorno com datas (XIRR, docs/planos/PLANO_RENTABILIDADE.md, R6): a taxa anual {@code x} que zera
 * {@code soma(CF_i / (1 + x)^(dias_i / 365))}. Newton a partir de 10% a.a.; sem convergencia, bisseccao em
 * [-99%, +1000%]. Sem troca de sinal nos fluxos, ou sem raiz, vazio.
 */
public final class Xirr {

    private Xirr() {
    }

    /** Um fluxo de caixa do investidor: negativo sai do bolso (aporte), positivo volta (venda, provento, saldo). */
    public record CashFlow(LocalDate date, double amount) {
    }

    public static Double of(List<CashFlow> flows) {
        if (flows.size() < 2) {
            return null;
        }
        boolean pos = flows.stream().anyMatch(f -> f.amount() > 0);
        boolean neg = flows.stream().anyMatch(f -> f.amount() < 0);
        if (!pos || !neg) {
            return null;
        }
        LocalDate t0 = flows.stream().map(CashFlow::date).min(LocalDate::compareTo).orElseThrow();
        double[] t = new double[flows.size()];
        double[] c = new double[flows.size()];
        for (int i = 0; i < flows.size(); i++) {
            t[i] = ChronoUnit.DAYS.between(t0, flows.get(i).date()) / 365.0;
            c[i] = flows.get(i).amount();
        }
        double x = 0.1;
        for (int it = 0; it < 100; it++) {
            double f = 0;
            double df = 0;
            for (int i = 0; i < c.length; i++) {
                double v = Math.pow(1 + x, -t[i]);
                f += c[i] * v;
                df += -t[i] * c[i] * v / (1 + x);
            }
            if (Math.abs(f) < 1e-7) {
                return x;
            }
            if (df == 0 || Double.isNaN(df)) {
                break;
            }
            double next = x - f / df;
            if (next <= -0.99 || Double.isNaN(next) || Double.isInfinite(next)) {
                break;
            }
            if (Math.abs(next - x) < 1e-10) {
                return next;
            }
            x = next;
        }
        return bisection(t, c);
    }

    private static Double bisection(double[] t, double[] c) {
        double lo = -0.99;
        double hi = 10;
        double flo = npv(lo, t, c);
        double fhi = npv(hi, t, c);
        if (Double.isNaN(flo) || Double.isNaN(fhi) || flo * fhi > 0) {
            return null;
        }
        for (int it = 0; it < 200; it++) {
            double mid = (lo + hi) / 2;
            double fm = npv(mid, t, c);
            if (Math.abs(fm) < 1e-7 || hi - lo < 1e-10) {
                return mid;
            }
            if (fm * flo < 0) {
                hi = mid;
            } else {
                lo = mid;
                flo = fm;
            }
        }
        return (lo + hi) / 2;
    }

    private static double npv(double x, double[] t, double[] c) {
        double f = 0;
        for (int i = 0; i < c.length; i++) {
            f += c[i] * Math.pow(1 + x, -t[i]);
        }
        return f;
    }
}
