package com.activest.domain.fund;

import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.fixedincome.FixedIncomeTax;
import com.activest.domain.fixedincome.FixedIncomeTaxRule;
import com.activest.domain.fixedincome.FixedIncomeTaxTable;
import com.activest.domain.fixedincome.TaxRegime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Imposto dos fundos de investimento (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, C7–C9, D17, D21, D22). Regra pura: as
 * alíquotas vêm de {@code fixed_income_tax_rules} (V12), as cotas do come-cotas de quem chama.
 *
 * <ul>
 *   <li><b>Regime</b> ({@link #regime}): Previdência sem imposto (o IR é do plano PGBL/VGBL — D21); Ações =
 *   {@link TaxRegime#FUND_EQUITY}; demais pela tributação de longo prazo do cadastro.</li>
 *   <li><b>Come-cotas</b>: no último dia útil de maio e de novembro, por lote, IR antecipado sobre o ganho desde a
 *   cota-base, pago em cotas (cotas retidas = IR ÷ cota do dia). Cota abaixo da base não retém (a compensação de
 *   prejuízo fica fora — limitação registrada).</li>
 *   <li><b>Resgate</b>: lotes por PEPS; IOF regressivo nos 30 primeiros dias (longo e curto prazo); IR pela alíquota
 *   do prazo sobre o ganho menos o IOF, descontado o come-cotas já retido daquelas cotas. Truncado nos centavos.</li>
 * </ul>
 */
public final class FundTax {

    private static final int SHARES_SCALE = 8;
    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 12;

    /** Uma transação do fundo, como o cálculo enxerga. */
    public record Tx(UUID id, boolean buy, LocalDate date, BigDecimal shares, BigDecimal price, BigDecimal total) {
    }

    /** Come-cotas de um lote. */
    public record ComeCotas(UUID lot, LocalDate date, BigDecimal quote, BigDecimal baseQuote, BigDecimal sharesBefore,
                            BigDecimal sharesWithheld, BigDecimal tax, BigDecimal rate) {
    }

    /** Imposto de um resgate. */
    public record SaleTax(UUID sale, BigDecimal gross, BigDecimal incomeTax, BigDecimal iof, BigDecimal net) {
    }

    /** O que sobra de um lote depois de come-cotas e resgates: base do líquido estimado (C10). */
    public record OpenLot(UUID id, LocalDate date, BigDecimal price, BigDecimal shares, BigDecimal ccPaid) {
    }

    public record Result(List<ComeCotas> comeCotas, Map<UUID, SaleTax> sales, List<OpenLot> openLots) {
    }

    private FundTax() {
    }

    /** Regime do fundo pela categoria e pela tributação de longo prazo; nulo = sem imposto (Previdência). */
    public static TaxRegime regime(String category, Boolean longTermTax) {
        if (FundCategory.PREVIDENCIA.equals(category)) {
            return null;
        }
        if (FundCategory.ACOES.equals(category)) {
            return TaxRegime.FUND_EQUITY;
        }
        return Boolean.FALSE.equals(longTermTax) ? TaxRegime.FUND_SHORT_TERM : TaxRegime.FUND_LONG_TERM;
    }

    /** Últimos dias úteis de maio e de novembro entre duas datas (inclusive). */
    public static List<LocalDate> comeCotasDates(LocalDate from, LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        for (int y = from.getYear(); y <= to.getYear(); y++) {
            for (Month m : new Month[]{Month.MAY, Month.NOVEMBER}) {
                LocalDate d = LocalDate.of(y, m, 1).withDayOfMonth(LocalDate.of(y, m, 1).lengthOfMonth());
                while (!BusinessCalendar.isBusinessDay(d)) {
                    d = d.minusDays(1);
                }
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    /**
     * Calcula come-cotas e imposto dos resgates.
     *
     * @param regime  nulo = sem imposto (Previdência): nenhum come-cotas e resgate líquido = bruto
     * @param rules   linhas de {@code fixed_income_tax_rules}
     * @param txs     transações do fundo, em qualquer ordem
     * @param quoteOn cota de uma data de come-cotas (nula = sem cota: o evento é pulado)
     * @param today   até quando gerar come-cotas
     */
    public static Result calculate(TaxRegime regime, List<FixedIncomeTaxRule> rules, List<Tx> txs,
                                   Function<LocalDate, BigDecimal> quoteOn, LocalDate today) {
        List<Tx> ordered = txs.stream().sorted(Comparator.comparing(Tx::date)
                .thenComparing(t -> t.buy() ? 0 : 1)).toList();
        Map<UUID, SaleTax> sales = new HashMap<>();
        List<ComeCotas> events = new ArrayList<>();
        if (ordered.isEmpty()) {
            return new Result(events, sales, List.of());
        }
        FixedIncomeTaxTable own = regime == null ? null : FixedIncomeTaxTable.ofRegime(rules, regime);
        FixedIncomeTaxTable general = new FixedIncomeTaxTable(rules);
        List<LocalDate> ccDates = regime == null || regime == TaxRegime.FUND_EQUITY ? List.of()
                : comeCotasDates(ordered.get(0).date(), today);
        List<Lot> lots = new ArrayList<>();
        int next = 0;
        for (LocalDate cc : ccDates) {
            while (next < ordered.size() && !ordered.get(next).date().isAfter(cc)) {
                apply(ordered.get(next++), lots, regime, own, general, sales);
            }
            BigDecimal q = quoteOn.apply(cc);
            if (q == null || q.signum() <= 0) {
                continue;
            }
            BigDecimal rate = own.rate(FixedIncomeTax.CC, null, 0, cc);
            for (Lot lot : lots) {
                if (lot.shares.signum() <= 0 || q.compareTo(lot.base) <= 0 || rate.signum() <= 0) {
                    continue;
                }
                BigDecimal tax = q.subtract(lot.base).multiply(lot.shares).multiply(rate)
                        .setScale(MONEY_SCALE, RoundingMode.DOWN);
                BigDecimal withheld = tax.divide(q, SHARES_SCALE, RoundingMode.HALF_UP);
                events.add(new ComeCotas(lot.id, cc, q, lot.base, lot.shares, withheld, tax, rate));
                lot.shares = lot.shares.subtract(withheld);
                lot.ccPaid = lot.ccPaid.add(tax);
                lot.base = q;
            }
        }
        while (next < ordered.size()) {
            apply(ordered.get(next++), lots, regime, own, general, sales);
        }
        List<OpenLot> open = lots.stream().filter(l -> l.shares.signum() > 0)
                .map(l -> new OpenLot(l.id, l.date, l.price, l.shares, l.ccPaid)).toList();
        return new Result(events, sales, open);
    }

    /**
     * Líquido estimado de um resgate total na data, à cota {@code price} (C10): bruto = cotas × cota; IOF e IR lote a
     * lote, como no resgate. Previdência ({@code regime} nulo): líquido = bruto.
     */
    public static SaleTax estimate(TaxRegime regime, List<FixedIncomeTaxRule> rules, List<OpenLot> openLots,
                                   BigDecimal price, LocalDate date) {
        FixedIncomeTaxTable own = regime == null ? null : FixedIncomeTaxTable.ofRegime(rules, regime);
        FixedIncomeTaxTable general = new FixedIncomeTaxTable(rules);
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal ir = BigDecimal.ZERO;
        BigDecimal iof = BigDecimal.ZERO;
        for (OpenLot lot : openLots) {
            shares = shares.add(lot.shares());
            if (regime == null) {
                continue;
            }
            BigDecimal[] t = lotTax(regime, own, general, lot.date(), lot.price(), lot.ccPaid(), lot.shares(), price,
                    date);
            ir = ir.add(t[0]);
            iof = iof.add(t[1]);
        }
        BigDecimal gross = shares.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        return new SaleTax(null, gross, ir, iof, gross.subtract(ir).subtract(iof));
    }

    /** IR e IOF de {@code take} cotas de um lote vendidas à cota {@code price} na data; {@code ccPart} já retido. */
    private static BigDecimal[] lotTax(TaxRegime regime, FixedIncomeTaxTable own, FixedIncomeTaxTable general,
                                       LocalDate lotDate, BigDecimal lotPrice, BigDecimal ccPart, BigDecimal take,
                                       BigDecimal price, LocalDate date) {
        long days = Math.max(0, ChronoUnit.DAYS.between(lotDate, date));
        BigDecimal gain = price.subtract(lotPrice).multiply(take);
        if (gain.signum() <= 0) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
        BigDecimal iofRate = regime == TaxRegime.FUND_EQUITY ? BigDecimal.ZERO
                : general.rate(FixedIncomeTax.IOF, null, days, date);
        BigDecimal lotIof = gain.multiply(iofRate).setScale(MONEY_SCALE, RoundingMode.DOWN);
        BigDecimal irRate = own.rate(FixedIncomeTax.IR, null, days, date);
        BigDecimal lotIr = gain.subtract(lotIof).multiply(irRate).subtract(ccPart)
                .setScale(MONEY_SCALE, RoundingMode.DOWN);
        return new BigDecimal[]{lotIr.signum() > 0 ? lotIr : BigDecimal.ZERO, lotIof};
    }

    private static final class Lot {
        final UUID id;
        final LocalDate date;
        final BigDecimal price;
        BigDecimal shares;
        BigDecimal base;
        BigDecimal ccPaid = BigDecimal.ZERO;

        Lot(Tx t) {
            this.id = t.id();
            this.date = t.date();
            this.price = t.price();
            this.shares = t.shares();
            this.base = t.price();
        }
    }

    private static void apply(Tx t, List<Lot> lots, TaxRegime regime, FixedIncomeTaxTable own,
                              FixedIncomeTaxTable general, Map<UUID, SaleTax> sales) {
        if (t.buy()) {
            if (t.shares() != null && t.shares().signum() > 0) {
                lots.add(new Lot(t));
            }
            return;
        }
        BigDecimal gross = t.total() != null ? t.total()
                : t.shares().multiply(t.price()).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal remaining = t.shares() == null ? BigDecimal.ZERO : t.shares();
        BigDecimal ir = BigDecimal.ZERO;
        BigDecimal iof = BigDecimal.ZERO;
        for (Lot lot : lots) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (lot.shares.signum() <= 0) {
                continue;
            }
            BigDecimal take = remaining.min(lot.shares);
            BigDecimal ccPart = lot.ccPaid.multiply(take).divide(lot.shares, RATIO_SCALE, RoundingMode.HALF_UP);
            if (regime != null) {
                BigDecimal[] tax = lotTax(regime, own, general, lot.date, lot.price, ccPart, take, t.price(), t.date());
                ir = ir.add(tax[0]);
                iof = iof.add(tax[1]);
            }
            lot.ccPaid = lot.ccPaid.subtract(ccPart);
            lot.shares = lot.shares.subtract(take);
            remaining = remaining.subtract(take);
        }
        sales.put(t.id(), new SaleTax(t.id(), gross, ir, iof, gross.subtract(ir).subtract(iof)));
    }
}
