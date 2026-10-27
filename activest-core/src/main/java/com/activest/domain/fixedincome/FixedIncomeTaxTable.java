package com.activest.domain.fixedincome;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * As regras de IR e IOF da V52 carregadas uma vez por rodada, e a busca da aliquota de um lote
 * (docs/planos/PLANO_VENDA_RENDA_FIXA.md, R6). Regra pura, sem banco: recebe as linhas prontas.
 *
 * <p>Precedencia: a linha do produto ({@code asset_id}) que cobre o prazo e a data vence a geral do mesmo
 * imposto. Se o produto nao tem linha vigente na data (ex.: uma isencao que acabou), vale a geral.</p>
 *
 * <p>A vigencia que vale e a da <b>data do resgate</b>; o prazo e contado em dias corridos desde o aporte
 * (IN RFB 1.585/2015, art. 46, § 9º). Sem regra nenhuma, a aliquota e zero.</p>
 */
public final class FixedIncomeTaxTable {

    /** As duas aliquotas que valem para um lote num resgate. */
    public record Rates(long days, BigDecimal iof, BigDecimal ir) {

        /**
         * Quanto do RENDIMENTO vai em imposto: o IOF sai primeiro e o IR incide sobre o que sobra
         * ({@code iof + (1 - iof) x ir}).
         */
        public BigDecimal onIncome() {
            return iof.add(BigDecimal.ONE.subtract(iof).multiply(ir));
        }
    }

    private final List<FixedIncomeTaxRule> rules;

    /** As regras da renda fixa e do Tesouro: só as linhas {@link TaxRegime#GENERAL} (as de fundo ficam de fora). */
    public FixedIncomeTaxTable(List<FixedIncomeTaxRule> rules) {
        this(rules, TaxRegime.GENERAL);
    }

    private FixedIncomeTaxTable(List<FixedIncomeTaxRule> rules, TaxRegime regime) {
        this.rules = rules.stream().filter(r -> r.getRegime() == null ? regime == TaxRegime.GENERAL : r.getRegime() == regime)
                .toList();
    }

    /**
     * As linhas de um regime de fundo (V12): IR e come-cotas ({@link FixedIncomeTax#CC}) do regime. O IOF dos fundos
     * de longo e curto prazo vem da tabela geral (ver {@code FundTaxTable}).
     */
    public static FixedIncomeTaxTable ofRegime(List<FixedIncomeTaxRule> rules, TaxRegime regime) {
        return new FixedIncomeTaxTable(rules, regime);
    }

    /** Sem regras: tudo isento. Para quem nao tem a tabela (ex.: testes da curva). */
    public static FixedIncomeTaxTable empty() {
        return new FixedIncomeTaxTable(List.of());
    }

    public static long days(LocalDate applied, LocalDate redeemed) {
        return Math.max(0, ChronoUnit.DAYS.between(applied, redeemed));
    }

    /** As aliquotas do lote aplicado em {@code applied} e resgatado em {@code redeemed}. */
    public Rates rates(Long assetId, LocalDate applied, LocalDate redeemed) {
        long days = days(applied, redeemed);
        return new Rates(days, rate(FixedIncomeTax.IOF, assetId, days, redeemed),
                rate(FixedIncomeTax.IR, assetId, days, redeemed));
    }

    public BigDecimal rate(FixedIncomeTax tax, Long assetId, long days, LocalDate date) {
        FixedIncomeTaxRule own = assetId == null ? null : find(tax, assetId, days, date);
        FixedIncomeTaxRule rule = own != null ? own : find(tax, null, days, date);
        return rule != null ? rule.getRate() : BigDecimal.ZERO;
    }

    private FixedIncomeTaxRule find(FixedIncomeTax tax, Long assetId, long days, LocalDate date) {
        for (FixedIncomeTaxRule r : rules) {
            boolean sameAsset = assetId == null ? r.getAssetId() == null : assetId.equals(r.getAssetId());
            if (r.getTax() == tax && sameAsset && r.coversDays(days) && r.covers(date)) {
                return r;
            }
        }
        return null;
    }
}
