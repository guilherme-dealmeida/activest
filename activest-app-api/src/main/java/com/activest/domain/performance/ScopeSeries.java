package com.activest.domain.performance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Um conjunto de posicoes somado dia a dia (docs/planos/PLANO_RENTABILIDADE.md, 4.3): a carteira consolidada, uma
 * classe ou um ativo. O TWR e calculado sobre esta soma, nunca como media dos ativos. Regra pura.
 *
 * <p><b>Disponibilidade</b> (R10): um dia e indisponivel quando alguma posicao exposta nele (saldo ontem ou hoje, ou
 * movimento) esta sem preco nele ou na vespera — o retorno do dia depende dos dois valores.</p>
 */
public final class ScopeSeries {

    public final List<LocalDate> dates;
    public final double[] value;
    public final double[] buy;
    public final double[] sell;
    public final double[] dist;
    public final boolean[] unavailable;
    public final boolean[] stale;
    public final boolean[] estimated;
    /** Posicoes do recorte (para breakdown e cobertura). */
    public final List<PortfolioTimeline.PositionSeries> members;
    public final PerformanceBasis basis;

    private ScopeSeries(List<LocalDate> dates, List<PortfolioTimeline.PositionSeries> members, PerformanceBasis basis) {
        int n = dates.size();
        this.dates = dates;
        this.members = members;
        this.basis = basis;
        value = new double[n];
        buy = new double[n];
        sell = new double[n];
        dist = new double[n];
        unavailable = new boolean[n];
        stale = new boolean[n];
        estimated = new boolean[n];
        boolean[] missing = new boolean[n];
        for (PortfolioTimeline.PositionSeries p : members) {
            for (int i = 0; i < n; i++) {
                value[i] += p.value(i, basis);
                buy[i] += p.buy()[i];
                sell[i] += p.sell(i, basis);
                dist[i] += p.dist(i, basis);
                if (p.held()[i]) {
                    PriceStatus s = p.status()[i];
                    if (s == PriceStatus.MISSING) {
                        missing[i] = true;
                    } else if (s == PriceStatus.STALE) {
                        stale[i] = true;
                    } else if (s == PriceStatus.ESTIMATED) {
                        estimated[i] = true;
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            unavailable[i] = missing[i] || (i > 0 && missing[i - 1] && exposedAny(i));
        }
    }

    private boolean exposedAny(int i) {
        for (PortfolioTimeline.PositionSeries p : members) {
            if (p.exposed(i)) {
                return true;
            }
        }
        return false;
    }

    public static ScopeSeries of(PortfolioTimeline t, Predicate<PortfolioTimeline.PositionSeries> filter,
                                 PerformanceBasis basis) {
        List<PortfolioTimeline.PositionSeries> members = t.positions().stream().filter(filter).toList();
        return new ScopeSeries(t.dates(), members, basis);
    }

    public int size() {
        return dates.size();
    }

    /** Ha capital no dia: valor na vespera ou aporte. */
    public boolean active(int i) {
        return denominator(i) > 0.005;
    }

    /** Base do retorno do dia: valor da vespera + aportes (comeco do dia). */
    public double denominator(int i) {
        return (i > 0 ? value[i - 1] : 0) + buy[i];
    }

    /** Ganho do dia em reais: valor + retiradas + proventos - valor da vespera - aportes. */
    public double gain(int i) {
        return value[i] + sell[i] + dist[i] - denominator(i);
    }

    /** Primeiro e ultimo dia com capital; {-1,-1} se nunca. */
    public int[] span() {
        int first = -1;
        int last = -1;
        for (int i = 0; i < size(); i++) {
            if (active(i) || value[i] > 0.005) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        return new int[]{first, last};
    }

    /** Uma lacuna de preco de um ativo: o intervalo e o motivo (R10). */
    public record Gap(String ticker, LocalDate from, LocalDate to, PriceStatus status) {
    }

    /** Intervalos sem preco (MISSING) e com ultimo negocio (STALE) ou estimados, por ativo, entre os indices dados. */
    public List<Gap> gaps(int from, int to) {
        List<Gap> out = new ArrayList<>();
        for (PortfolioTimeline.PositionSeries p : members) {
            PriceStatus cur = null;
            int start = -1;
            for (int i = Math.max(0, from); i <= to + 1; i++) {
                PriceStatus s = i <= to && i < size() && p.held()[i] && p.status()[i] != PriceStatus.OK
                        ? p.status()[i] : null;
                if (s != cur) {
                    if (cur != null) {
                        out.add(new Gap(label(p), dates.get(start), dates.get(i - 1), cur));
                    }
                    cur = s;
                    start = i;
                }
            }
        }
        return merge(out);
    }

    private static String label(PortfolioTimeline.PositionSeries p) {
        return p.noteLabel() != null && p.typeName() != null && p.typeName().equals("RENDA_FIXA")
                ? p.ticker() + " (" + p.noteLabel() + ")" : p.ticker();
    }

    /** Mesmo ativo em mais de uma posicao (titulos do Tesouro) com o mesmo intervalo: uma linha so. */
    private static List<Gap> merge(List<Gap> gaps) {
        Map<String, Gap> unique = new LinkedHashMap<>();
        for (Gap g : gaps) {
            unique.putIfAbsent(g.ticker() + g.from() + g.to() + g.status(), g);
        }
        return new ArrayList<>(unique.values());
    }

    /** Tickers sem preco em algum dia do intervalo. */
    public Set<String> missingTickers(int from, int to) {
        Set<String> out = new LinkedHashSet<>();
        for (Gap g : gaps(from, to)) {
            if (g.status() == PriceStatus.MISSING) {
                out.add(g.ticker());
            }
        }
        return out;
    }
}
