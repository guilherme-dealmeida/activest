package com.primevest.domain.dividend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * O jeito de calcular o IR retido de um provento — o que nao muda com a lei
 * (docs/planos/PLANO_IR_PROVENTOS.md). As aliquotas vem de {@link WithholdingTaxRule}.
 *
 * <p>A conta e a da B3, conferida centavo a centavo no extrato:</p>
 * <ol>
 *   <li>bruto = valor por cota x cotas, <b>truncado</b> nos centavos;</li>
 *   <li>IR = bruto x aliquota, <b>truncado</b> nos centavos;</li>
 *   <li>TOTAL liquido = bruto - IR.</li>
 * </ol>
 * <p>O liquido por cota (8 casas) e so para exibicao: multiplica-lo pelas cotas
 * erra 1 centavo (BBAS3, 125 x 0,04923931 = 6,15; a B3 pagou 6,16).</p>
 *
 * <p>Sem estado e sem Spring: recebe as regras prontas, para o teste nao
 * precisar de banco.</p>
 */
public final class WithholdingTaxCalculator {

    static final int NET_PER_SHARE_SCALE = 8;

    private WithholdingTaxCalculator() {
    }

    /**
     * Dados do provento que decidem o imposto.
     *
     * @param assetTypeId tipo do ativo; nulo = sem regra
     */
    public record Input(DividendType type, Long assetTypeId, LocalDate approvedOn, LocalDate dataCom,
                        LocalDate paymentDate, BigDecimal valuePerShare, BigDecimal shares) {
    }

    /**
     * Resultado. Sem regra: {@code rule}, {@code rate}, {@code tax},
     * {@code netPerShare} e {@code net} nulos; {@code gross} sempre preenchido.
     */
    public record Result(WithholdingTaxRule rule, BigDecimal rate, BigDecimal gross, BigDecimal tax,
                         BigDecimal netPerShare, BigDecimal net) {

        public boolean hasRule() {
            return rule != null;
        }
    }

    /** Bruto da B3: valor por cota x cotas, truncado nos centavos. */
    public static BigDecimal gross(BigDecimal valuePerShare, BigDecimal shares) {
        return valuePerShare.multiply(shares).setScale(2, RoundingMode.DOWN);
    }

    public static Result calculate(List<WithholdingTaxRule> rules, Input in) {
        BigDecimal gross = gross(in.valuePerShare(), in.shares());
        WithholdingTaxRule rule = find(rules, in);
        if (rule == null) {
            return new Result(null, null, gross, null, null, null);
        }
        BigDecimal rate = rule.getRate();
        BigDecimal tax = gross.multiply(rate).setScale(2, RoundingMode.DOWN);
        BigDecimal netPerShare = in.valuePerShare().multiply(BigDecimal.ONE.subtract(rate))
                .setScale(NET_PER_SHARE_SCALE, RoundingMode.HALF_UP);
        return new Result(rule, rate, gross, tax, netPerShare, gross.subtract(tax));
    }

    /**
     * Regra vigente para o provento: mesmo tipo de provento e de ativo, com a
     * data da base da regra dentro da vigencia. Nula = sem regra.
     */
    static WithholdingTaxRule find(List<WithholdingTaxRule> rules, Input in) {
        if (in.type() == null || in.assetTypeId() == null) {
            return null;
        }
        for (WithholdingTaxRule r : rules) {
            if (r.getDividendType() != in.type() || !in.assetTypeId().equals(r.getAssetTypeId())) {
                continue;
            }
            LocalDate date = baseDate(r.getBaseDate(), in);
            if (date != null && r.covers(date)) {
                return r;
            }
        }
        return null;
    }

    /** A data que cai na vigencia, conforme a base da regra. */
    static LocalDate baseDate(TaxBaseDate base, Input in) {
        if (base == TaxBaseDate.CREDIT) {
            return firstNonNull(in.approvedOn(), in.dataCom(), in.paymentDate());
        }
        return firstNonNull(in.paymentDate(), in.dataCom());
    }

    private static LocalDate firstNonNull(LocalDate... dates) {
        for (LocalDate d : dates) {
            if (d != null) {
                return d;
            }
        }
        return null;
    }
}
