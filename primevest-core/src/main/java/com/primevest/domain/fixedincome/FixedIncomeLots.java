package com.primevest.domain.fixedincome;

import com.primevest.domain.calendar.BusinessCalendar;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * O titulo de renda fixa privada aporte a aporte (docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 4.2). Regra pura,
 * sem banco.
 *
 * <p>Cada compra vira um <b>lote</b> (R1). Todos rendem pelo mesmo fator do dia da {@link FixedIncomeCurve} — e o
 * mesmo titulo —, entao a soma dos lotes e o saldo da curva. O que os lotes acrescentam: o aplicado e o rendimento
 * de cada aporte, a idade dele (que decide a aliquota de IR e IOF) e, com isso, o bruto de uma venda lancada pelo
 * liquido.</p>
 *
 * <ul>
 *   <li><b>Venda parcial pelo liquido</b> (R3, R7): o bruto e o que, tirado dos lotes pela {@link SaleRule}, deixa o
 *   liquido digitado depois do IOF e do IR de cada parte. PROPORCIONAL tem formula fechada
 *   ({@code bruto = liquido / (1 - t)}); UEPS e PEPS percorrem os lotes na ordem. O lote consumido em parte cede
 *   aplicado e rendimento na mesma proporcao e mantem a data.</li>
 *   <li><b>Acima do disponivel</b> (R9, D4): o resgate prova que o titulo valia mais; os lotes caem para o centavo
 *   da regra F e seguem rendendo.</li>
 *   <li><b>Liquidacao total</b> (R8): todos os lotes saem; a diferenca entre o liquido digitado e o liquido estimado
 *   e tratada como rendimento a mais (o "ajuste da liquidacao"), e o bruto e o que fecha o liquido com o imposto
 *   desse rendimento.</li>
 *   <li><b>Venda sem liquido</b> (bruto informado): sai pela regra, e o liquido e o bruto menos os impostos.</li>
 *   <li><b>Sem curva</b> (TR, serie ausente): os lotes nao rendem — rendimento zero, imposto zero, bruto =
 *   liquido. O status volta UNSUPPORTED com o motivo, como na curva.</li>
 * </ul>
 */
public final class FixedIncomeLots {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal RESIDUE = new BigDecimal("0.01");
    /** Menos de meio centavo: resto de conta, nao saldo. */
    private static final BigDecimal DUST = new BigDecimal("0.005");
    private static final int MONEY = 2;

    private FixedIncomeLots() {
    }

    public enum Kind { BUY, SELL }

    /**
     * Um movimento do titulo, na ordem cronologica.
     *
     * @param key            identifica a venda no resultado (o id da transacao)
     * @param amount         compra: o valor aplicado; venda sem liquido: o bruto
     * @param net            venda: o liquido recebido (fonte do bruto); nulo = venda pelo bruto
     * @param fullSettlement venda marcada como liquidacao total
     */
    public record Move(Object key, LocalDate date, Kind kind, BigDecimal amount, BigDecimal net,
                       boolean fullSettlement) {

        public static Move buy(Object key, LocalDate date, BigDecimal amount) {
            return new Move(key, date, Kind.BUY, amount, null, false);
        }

        public static Move sellNet(Object key, LocalDate date, BigDecimal net, boolean full) {
            return new Move(key, date, Kind.SELL, null, net, full);
        }

        public static Move sellGross(Object key, LocalDate date, BigDecimal gross, boolean full) {
            return new Move(key, date, Kind.SELL, gross, null, full);
        }
    }

    /**
     * O que uma venda virou.
     *
     * @param adjustment     liquidacao total, ou venda parcial acima do disponivel: bruto menos o saldo da curva (na
     *                       liquidacao pode ser negativo); nulo nas demais vendas
     * @param availableNet   liquido disponivel estimado na data, antes da venda
     * @param grossBefore    saldo bruto na data, antes da venda
     * @param aboveAvailable venda parcial acima do disponivel (R9 / D4)
     */
    public record SaleOutcome(Object key, LocalDate date, BigDecimal gross, BigDecimal iof, BigDecimal ir,
                              BigDecimal net, BigDecimal adjustment, BigDecimal availableNet, BigDecimal grossBefore,
                              boolean aboveAvailable) {
    }

    /** Um lote em aberto na data de referencia. */
    public record Lot(LocalDate date, long days, BigDecimal applied, BigDecimal balance, BigDecimal income,
                      BigDecimal iofRate, BigDecimal irRate, BigDecimal iof, BigDecimal ir, BigDecimal net) {
    }

    /**
     * @param balance      saldo bruto (soma dos lotes), 2 casas
     * @param applied      aplicado restante (soma dos lotes), 2 casas
     * @param estimatedNet liquido estimado se resgatado na data de referencia
     */
    public record Result(FixedIncomeCurve.Status status, String reason, LocalDate referenceDate, boolean estimated,
                         SaleRule rule, BigDecimal balance, BigDecimal applied, BigDecimal estimatedNet,
                         List<Lot> lots, Map<Object, SaleOutcome> sales) {

        public SaleOutcome sale(Object key) {
            return sales.get(key);
        }
    }

    /** Um lote em memoria, sem arredondar no meio (D12). */
    private static final class Open {
        final LocalDate date;
        final int seq;
        BigDecimal principal;
        BigDecimal balance;

        Open(LocalDate date, int seq, BigDecimal amount) {
            this.date = date;
            this.seq = seq;
            this.principal = amount;
            this.balance = amount;
        }

        BigDecimal income() {
            BigDecimal y = balance.subtract(principal, MC);
            return y.signum() > 0 ? y : BigDecimal.ZERO;
        }

        /** Tira {@code gross} do lote; aplicado e rendimento saem na mesma proporcao. */
        void take(BigDecimal gross) {
            if (balance.signum() <= 0) {
                return;
            }
            if (gross.compareTo(balance.subtract(DUST, MC)) >= 0) {
                principal = BigDecimal.ZERO; // consumido inteiro (a sobra de arredondamento nao vira lote)
                balance = BigDecimal.ZERO;
                return;
            }
            BigDecimal keep = BigDecimal.ONE.subtract(gross.divide(balance, MC), MC);
            principal = principal.multiply(keep, MC);
            balance = balance.subtract(gross, MC);
        }
    }

    public static Result run(FixedIncomeCurve.Terms terms, List<Move> moves, LocalDate referenceDate,
                             NavigableMap<LocalDate, BigDecimal> daily, NavigableMap<LocalDate, BigDecimal> monthly,
                             FixedIncomeTaxTable taxes, Long assetId, SaleRule rule) {
        SaleRule saleRule = rule != null ? rule : SaleRule.DEFAULT;
        FixedIncomeTaxTable table = taxes != null ? taxes : FixedIncomeTaxTable.empty();
        List<Move> sorted = new ArrayList<>(moves);
        sorted.sort(Comparator.comparing(Move::date)); // estavel: mantem a ordem do mesmo dia

        String reason = FixedIncomeCurve.check(terms, daily, monthly);
        if (reason == null && !sorted.isEmpty()) {
            reason = FixedIncomeCurve.beforeSeries(terms, sorted.get(0).date(), daily, monthly);
        }
        boolean accrues = reason == null;

        LocalDate maturity = terms.maturity();
        boolean matured = maturity != null && !maturity.isAfter(referenceDate);
        LocalDate lastAccrual = matured ? maturity.minusDays(1) : referenceDate;

        List<Open> lots = new ArrayList<>();
        Map<Object, SaleOutcome> sales = new LinkedHashMap<>();
        boolean[] estimated = {false};

        if (!sorted.isEmpty()) {
            LocalDate start = sorted.get(0).date();
            LocalDate lastMove = sorted.get(sorted.size() - 1).date();
            LocalDate end = lastMove.isAfter(lastAccrual) ? lastMove : lastAccrual;
            BigDecimal spreadDay = accrues ? FixedIncomeCurve.spreadDay(terms) : BigDecimal.ONE;
            int next = 0;
            int seq = 0;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                while (next < sorted.size() && sorted.get(next).date().equals(d)) {
                    Move m = sorted.get(next++);
                    if (m.kind() == Kind.BUY) {
                        lots.add(new Open(d, seq++, m.amount()));
                    } else {
                        SaleOutcome out = sell(lots, m, d, table, assetId, saleRule);
                        if (m.key() != null) {
                            sales.put(m.key(), out);
                        }
                    }
                }
                if (!accrues || d.isAfter(lastAccrual) || !BusinessCalendar.isBusinessDay(d)) {
                    continue;
                }
                BigDecimal factor = FixedIncomeCurve.dayFactor(terms, d, daily, monthly, spreadDay, estimated);
                if (factor == null) {
                    lastAccrual = daily.lastKey(); // a serie acabou antes da data de referencia
                    continue;
                }
                for (Open lot : lots) {
                    if (lot.balance.signum() > 0) {
                        lot.balance = lot.balance.multiply(factor, MC);
                    }
                }
            }
        }

        LocalDate ref = matured ? maturity : (accrues ? lastAccrual : referenceDate);
        List<Lot> view = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal applied = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (Open lot : lots) {
            if (money(lot.balance).signum() <= 0) {
                continue;
            }
            FixedIncomeTaxTable.Rates rates = table.rates(assetId, lot.date, ref);
            FixedIncomeTaxCalculator.Taxes t = FixedIncomeTaxCalculator.taxes(money(lot.income()), rates);
            BigDecimal b = money(lot.balance);
            view.add(new Lot(lot.date, rates.days(), money(lot.principal), b, money(lot.income()), rates.iof(),
                    rates.ir(), t.iof(), t.ir(), b.subtract(t.total())));
            balance = balance.add(lot.balance);
            applied = applied.add(lot.principal);
            net = net.add(b.subtract(t.total()));
        }
        FixedIncomeCurve.Status status = !accrues ? FixedIncomeCurve.Status.UNSUPPORTED
                : matured ? FixedIncomeCurve.Status.MATURED : FixedIncomeCurve.Status.OK;
        return new Result(status, reason, ref, estimated[0], saleRule, money(balance), money(applied), net,
                List.copyOf(view), sales);
    }

    // ------------------------------------------------------------------ venda

    private static SaleOutcome sell(List<Open> all, Move m, LocalDate d, FixedIncomeTaxTable table, Long assetId,
                                    SaleRule rule) {
        List<Open> open = all.stream().filter(l -> l.balance.signum() > 0).toList();
        int n = open.size();
        BigDecimal[] income = new BigDecimal[n];
        FixedIncomeTaxTable.Rates[] rates = new FixedIncomeTaxTable.Rates[n];
        BigDecimal grossBefore = BigDecimal.ZERO;
        BigDecimal availableNet = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            Open lot = open.get(i);
            income[i] = lot.income();
            rates[i] = table.rates(assetId, lot.date, d);
            grossBefore = grossBefore.add(lot.balance, MC);
            availableNet = availableNet.add(money(lot.balance)
                    .subtract(FixedIncomeTaxCalculator.taxes(money(income[i]), rates[i]).total()));
        }

        if (m.fullSettlement()) {
            return settle(open, m, d, income, rates, grossBefore, availableNet);
        }

        if (m.net() != null && m.net().compareTo(availableNet) > 0) {
            return overflow(open, m, d, income, rates, grossBefore, availableNet);
        }

        BigDecimal[] take = new BigDecimal[n];
        BigDecimal excessNet = BigDecimal.ZERO;
        if (m.net() != null) {
            excessNet = allocateNet(open, income, rates, m.net(), rule, take);
        } else {
            allocateGross(open, m.amount(), rule, take);
        }

        FixedIncomeTaxCalculator.Taxes total = FixedIncomeTaxCalculator.Taxes.NONE;
        BigDecimal taken = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            if (take[i] == null || take[i].signum() <= 0) {
                continue;
            }
            Open lot = open.get(i);
            BigDecimal part = income[i].multiply(take[i].divide(lot.balance, MC), MC);
            total = total.plus(FixedIncomeTaxCalculator.taxes(money(part), rates[i]));
            taken = taken.add(take[i], MC);
        }

        BigDecimal gross;
        BigDecimal net;
        if (m.net() != null) {
            net = m.net();
            gross = net.add(total.total());
        } else {
            gross = m.amount();
            net = gross.subtract(total.total());
        }
        gross = gross.setScale(MONEY, RoundingMode.HALF_UP);

        boolean above = excessNet.signum() > 0 || gross.compareTo(grossBefore) >= 0;
        if (above) {
            // D4: o resgate prova que o titulo valia mais; sobra o centavo da regra F, no lote mais novo
            for (Open lot : open) {
                lot.take(lot.balance);
            }
            if (!open.isEmpty()) {
                Open last = open.get(open.size() - 1);
                last.balance = RESIDUE;
                last.principal = BigDecimal.ZERO;
            }
        } else {
            // tira exatamente o bruto gravado: a diferenca para o alocado e so o truncamento dos impostos
            BigDecimal scale = taken.signum() > 0 ? gross.divide(taken, MC) : BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                if (take[i] != null && take[i].signum() > 0) {
                    open.get(i).take(take[i].multiply(scale, MC));
                }
            }
        }
        return new SaleOutcome(m.key(), d, gross, total.iof(), total.ir(), money(net), null,
                availableNet, money(grossBefore), m.net() != null && m.net().compareTo(availableNet) > 0);
    }

    /**
     * Quanto bruto sai de cada lote para entregar {@code net} de liquido (R7).
     *
     * @return o liquido que sobrou sem lote para cobrir (acima do disponivel)
     */
    private static BigDecimal allocateNet(List<Open> open, BigDecimal[] income, FixedIncomeTaxTable.Rates[] rates,
                                          BigDecimal net, SaleRule rule, BigDecimal[] take) {
        int n = open.size();
        BigDecimal[] keepRate = new BigDecimal[n]; // 1 - imposto por real de bruto do lote
        for (int i = 0; i < n; i++) {
            Open lot = open.get(i);
            BigDecimal taxPerGross = lot.balance.signum() > 0
                    ? rates[i].onIncome().multiply(income[i], MC).divide(lot.balance, MC) : BigDecimal.ZERO;
            keepRate[i] = BigDecimal.ONE.subtract(taxPerGross, MC);
        }
        if (rule == SaleRule.PROPORCIONAL) {
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal keep = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                gross = gross.add(open.get(i).balance, MC);
                keep = keep.add(open.get(i).balance.multiply(keepRate[i], MC), MC);
            }
            if (gross.signum() <= 0 || keep.signum() <= 0) {
                return net;
            }
            BigDecimal fraction = net.divide(keep, MC); // bruto / saldo = liquido / (saldo x (1 - t))
            BigDecimal excess = BigDecimal.ZERO;
            if (fraction.compareTo(BigDecimal.ONE) > 0) {
                excess = net.subtract(keep, MC);
                fraction = BigDecimal.ONE;
            }
            for (int i = 0; i < n; i++) {
                take[i] = open.get(i).balance.multiply(fraction, MC);
            }
            return excess;
        }
        BigDecimal remaining = net;
        for (int i : order(open, rule)) {
            if (remaining.signum() <= 0) {
                break;
            }
            Open lot = open.get(i);
            BigDecimal capacity = lot.balance.multiply(keepRate[i], MC);
            if (remaining.compareTo(capacity) >= 0) {
                take[i] = lot.balance;
                remaining = remaining.subtract(capacity, MC);
            } else {
                take[i] = remaining.divide(keepRate[i], MC);
                remaining = BigDecimal.ZERO;
            }
        }
        return remaining;
    }

    /** Quanto bruto sai de cada lote quando a venda veio pelo bruto. */
    private static void allocateGross(List<Open> open, BigDecimal gross, SaleRule rule, BigDecimal[] take) {
        int n = open.size();
        BigDecimal total = BigDecimal.ZERO;
        for (Open lot : open) {
            total = total.add(lot.balance, MC);
        }
        if (total.signum() <= 0) {
            return;
        }
        if (rule == SaleRule.PROPORCIONAL) {
            BigDecimal fraction = gross.divide(total, MC).min(BigDecimal.ONE);
            for (int i = 0; i < n; i++) {
                take[i] = open.get(i).balance.multiply(fraction, MC);
            }
            return;
        }
        BigDecimal remaining = gross;
        for (int i : order(open, rule)) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal b = open.get(i).balance;
            take[i] = remaining.min(b);
            remaining = remaining.subtract(take[i], MC);
        }
    }

    /** Liquidacao total (R8): todos os lotes saem; a diferenca para o estimado vira rendimento a mais. */
    private static SaleOutcome settle(List<Open> open, Move m, LocalDate d, BigDecimal[] income,
                                      FixedIncomeTaxTable.Rates[] rates, BigDecimal grossBefore,
                                      BigDecimal availableNet) {
        int n = open.size();
        BigDecimal gross0 = money(grossBefore);
        // rendimento a mais (delta), repartido pelos lotes na proporcao do saldo
        BigDecimal delta = m.net() != null
                ? grossUp(open, rates, grossBefore, m.net().subtract(availableNet))
                : m.amount().subtract(grossBefore, MC);
        FixedIncomeTaxCalculator.Taxes total = taxesWithDelta(open, income, rates, grossBefore, delta);
        BigDecimal gross;
        BigDecimal net;
        if (m.net() != null) {
            net = m.net();
            gross = net.add(total.total());
        } else {
            gross = m.amount();
            net = gross.subtract(total.total());
        }
        for (Open lot : open) {
            lot.take(lot.balance);
        }
        gross = gross.setScale(MONEY, RoundingMode.HALF_UP);
        return new SaleOutcome(m.key(), d, gross, total.iof(), total.ir(), money(net), gross.subtract(gross0),
                availableNet, gross0, false);
    }

    /**
     * Venda parcial pelo liquido acima do liquido disponivel, lancada mesmo assim (R9 -> D4; PENDENCIAS 27.5, revisto em
     * 15/10/2026): o que passou do disponivel e rendimento que a curva nao previu, e o banco o tributou. Como na
     * liquidacao total (R8), o excesso vira bruto pelas aliquotas dos lotes (gross-up) e entra no IR e no IOF; todos
     * os lotes saem e sobra o centavo da regra F no mais novo. O ajuste e o bruto menos o saldo da curva.
     */
    private static SaleOutcome overflow(List<Open> open, Move m, LocalDate d, BigDecimal[] income,
                                        FixedIncomeTaxTable.Rates[] rates, BigDecimal grossBefore,
                                        BigDecimal availableNet) {
        BigDecimal delta = grossUp(open, rates, grossBefore, m.net().subtract(availableNet));
        FixedIncomeTaxCalculator.Taxes total = taxesWithDelta(open, income, rates, grossBefore, delta);
        BigDecimal gross = m.net().add(total.total()).setScale(MONEY, RoundingMode.HALF_UP);
        for (Open lot : open) {
            lot.take(lot.balance);
        }
        if (!open.isEmpty()) {
            Open last = open.get(open.size() - 1);
            last.balance = RESIDUE;
            last.principal = BigDecimal.ZERO;
        }
        BigDecimal gross0 = money(grossBefore);
        return new SaleOutcome(m.key(), d, gross, total.iof(), total.ir(), money(m.net()), gross.subtract(gross0),
                availableNet, gross0, true);
    }

    /**
     * Rendimento a mais (bruto) que entrega {@code extraNet} de liquido: extraNet / (1 - imposto medio por real de
     * rendimento), com o imposto de cada lote pesado pelo saldo dele. Todos os lotes entram, inclusive o sem rendimento
     * ainda: a parte do excesso que cai nele e tributada pela idade dele.
     */
    private static BigDecimal grossUp(List<Open> open, FixedIncomeTaxTable.Rates[] rates, BigDecimal grossBefore,
                                      BigDecimal extraNet) {
        if (grossBefore.signum() <= 0) {
            return extraNet;
        }
        BigDecimal weighted = BigDecimal.ZERO;
        for (int i = 0; i < open.size(); i++) {
            weighted = weighted.add(rates[i].onIncome()
                    .multiply(open.get(i).balance.divide(grossBefore, MC), MC), MC);
        }
        return extraNet.divide(BigDecimal.ONE.subtract(weighted, MC), MC);
    }

    /** IR e IOF de todos os lotes saindo inteiros, com {@code delta} de rendimento repartido pelo saldo. */
    private static FixedIncomeTaxCalculator.Taxes taxesWithDelta(List<Open> open, BigDecimal[] income,
                                                                FixedIncomeTaxTable.Rates[] rates,
                                                                BigDecimal grossBefore, BigDecimal delta) {
        FixedIncomeTaxCalculator.Taxes total = FixedIncomeTaxCalculator.Taxes.NONE;
        for (int i = 0; i < open.size(); i++) {
            BigDecimal share = grossBefore.signum() > 0
                    ? delta.multiply(open.get(i).balance.divide(grossBefore, MC), MC) : BigDecimal.ZERO;
            total = total.plus(FixedIncomeTaxCalculator.taxes(money(income[i].add(share, MC)), rates[i]));
        }
        return total;
    }

    /** Indices dos lotes na ordem da regra: UEPS do mais novo, PEPS do mais antigo. */
    private static List<Integer> order(List<Open> open, SaleRule rule) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < open.size(); i++) {
            idx.add(i);
        }
        Comparator<Integer> byAge = Comparator.<Integer, LocalDate>comparing(i -> open.get(i).date)
                .thenComparing(i -> open.get(i).seq);
        idx.sort(rule == SaleRule.UEPS ? byAge.reversed() : byAge);
        return idx;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY, RoundingMode.HALF_EVEN);
    }
}
