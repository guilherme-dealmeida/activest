package com.activest.domain.currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fórmulas dos tributos do exterior (plano, seção 5 e regras C5–C7). Puras: recebem a regra e os valores em reais e
 * devolvem o tributo arredondado em 2 casas (HALF_EVEN, D26). Escolher a regra é do {@link ForeignTaxRuleService}.
 *
 * <p>Tudo aqui é <b>estimativa</b> por operação (D20): a apuração oficial é anual (Lei 14.754/2023) e compensa perdas
 * do ano, o que pode reduzir o imposto. A tela diz isso.</p>
 */
public final class ForeignTaxCalculator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ForeignTaxCalculator() {
    }

    /** Alíquota simples sobre a base (IOF de câmbio, retenção do país de origem). Base ≤ 0 → 0. */
    public static BigDecimal flat(BigDecimal base, ForeignTaxRule rule) {
        if (base == null || rule == null || base.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return base.multiply(rule.getRate()).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * IR brasileiro sobre o provento (C6): alíquota sobre o bruto em reais, menos o imposto já retido no exterior
     * (dedução limitada ao imposto daqui: nunca negativo). Desde 2024: 15% − 30% retido nos EUA = 0.
     */
    public static BigDecimal dividendIncomeTax(BigDecimal grossBase, BigDecimal foreignWithheld, ForeignTaxRule rule) {
        BigDecimal due = flat(grossBase, rule);
        BigDecimal credit = foreignWithheld == null ? BigDecimal.ZERO : foreignWithheld;
        return due.subtract(credit).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * IR estimado sobre o ganho da venda (C5), em reais.
     *
     * @param gain              venda em reais − custo médio em reais da quantidade vendida
     * @param monthSalesInBase  total das vendas de ativos do exterior no mês, em reais, incluindo esta (para a isenção
     *                          mensal do regime anterior a 2024)
     */
    public static BigDecimal saleIncomeTax(BigDecimal gain, BigDecimal monthSalesInBase, ForeignTaxRule rule) {
        if (gain == null || rule == null || gain.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        JsonNode b = brackets(rule);
        if (b != null && b.hasNonNull("monthlyExemption") && monthSalesInBase != null
            && monthSalesInBase.compareTo(b.get("monthlyExemption").decimalValue()) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (b == null || !b.has("brackets") || !b.get("brackets").isArray() || b.get("brackets").isEmpty()) {
            return gain.multiply(rule.getRate()).setScale(2, RoundingMode.HALF_EVEN);
        }
        // Progressivo por parcela (Lei 13.259/2016): cada faixa tributa só o que cai dentro dela.
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (JsonNode f : b.get("brackets")) {
            BigDecimal rate = f.get("rate").decimalValue();
            BigDecimal upper = f.hasNonNull("upTo") ? f.get("upTo").decimalValue() : null;
            if (gain.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal top = upper == null ? gain : gain.min(upper);
            tax = tax.add(top.subtract(lower).multiply(rate));
            if (upper == null) {
                break;
            }
            lower = upper;
        }
        return tax.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static JsonNode brackets(ForeignTaxRule rule) {
        if (rule.getBrackets() == null || rule.getBrackets().isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(rule.getBrackets());
        } catch (Exception e) {
            throw new IllegalStateException("foreign_tax_rules " + rule.getId() + ": brackets inválido", e);
        }
    }
}
