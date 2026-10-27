package com.activest.domain.dividend;

import com.activest.domain.portfolio.PortfolioAsset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * A posicao que recebeu o provento. E o unico caminho ate o ativo
     * (provento -> posicao -> ativo) e ate o dono (-> carteira -> usuario).
     *
     * <p>Antes havia tambem asset_id, um segundo caminho para o mesmo ativo que
     * podia divergir do da posicao, e portfolio_asset_id era opcional. Provento
     * sem posicao nao tinha dono e sumia ate para quem o recebeu.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_asset_id", nullable = false)
    private PortfolioAsset portfolioAsset;

    @Column(name = "amount_per_share", precision = 18, scale = 8, nullable = false)
    private BigDecimal amountPerShare;

    /** Total BRUTO: valor por cota x cotas, truncado nos centavos (conta da B3). */
    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DividendType type;

    /**
     * Evento do catalogo que gerou este provento. Nulo no MANUAL (V20).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_event_id")
    private AssetCashEvent cashEvent;

    /** Cotas da posicao na data-com, gravadas para conferencia (V20). */
    @Column(name = "shares_on_date", precision = 18, scale = 8)
    private BigDecimal sharesOnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DividendOrigin origin = DividendOrigin.MANUAL;

    // ------------------------------------------------------------------
    // IR retido na fonte (V23, docs/planos/PLANO_IR_PROVENTOS.md). Com regra, os cinco
    // andam juntos; sem regra para o tipo, todos nulos.
    // ------------------------------------------------------------------

    /** Regra que calculou o imposto; nula = sem regra para o tipo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withholding_rule_id")
    private WithholdingTaxRule withholdingRule;

    /** Copia da aliquota aplicada (0 = isento). */
    @Column(name = "withholding_rate", precision = 6, scale = 4)
    private BigDecimal withholdingRate;

    /** IR retido, truncado nos centavos. */
    @Column(name = "withholding_tax", precision = 18, scale = 2)
    private BigDecimal withholdingTax;

    /** Liquido por cota, 8 casas — so exibicao. */
    @Column(name = "net_amount_per_share", precision = 18, scale = 8)
    private BigDecimal netAmountPerShare;

    /** TOTAL liquido = totalAmount - withholdingTax. */
    @Column(name = "net_amount", precision = 18, scale = 2)
    private BigDecimal netAmount;

    // ----- moeda (V9, docs/planos/PLANO_CADASTRO_MOEDAS.md, C6) -----
    // amount_per_share, total_amount e net_* continuam na MOEDA BASE. Provento de ativo de outra moeda guarda o
    // valor por cota original, o cambio (PTAX compra da data de pagamento, D24) e a retencao do pais de origem.

    @Column(name = "currency_code", nullable = false, length = 3)
    @Builder.Default
    private String currencyCode = com.activest.domain.currency.Currency.BRL;

    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal fxRate = BigDecimal.ONE;

    @Column(name = "original_amount_per_share", precision = 18, scale = 8)
    private BigDecimal originalAmountPerShare;

    @Column(name = "original_total_amount", precision = 18, scale = 2)
    private BigDecimal originalTotalAmount;

    @Column(name = "foreign_withholding_rate", precision = 6, scale = 4)
    private BigDecimal foreignWithholdingRate;

    @Column(name = "foreign_withholding_tax", precision = 18, scale = 2)
    private BigDecimal foreignWithholdingTax;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "foreign_tax_rule_id")
    private com.activest.domain.currency.ForeignTaxRule foreignTaxRule;

    /** IR brasileiro estimado sobre provento do exterior, já deduzida a retenção do país de origem (V10, C6). */
    @Column(name = "foreign_income_tax", precision = 18, scale = 2)
    private BigDecimal foreignIncomeTax;

    /**
     * Quando a regra foi aplicada. Nulo = ainda nao calculado (diferente de
     * "sem regra"): o {@link DividendTaxVerifier} recalcula na subida.
     */
    @Column(name = "tax_checked_at")
    private LocalDateTime taxCheckedAt;

    /** Grava o resultado do calculo de IR e marca como conferido agora. */
    public void applyTax(WithholdingTaxCalculator.Result tax) {
        clearForeign();
        this.totalAmount = tax.gross();
        this.withholdingRule = tax.rule();
        this.withholdingRate = tax.rate();
        this.withholdingTax = tax.tax();
        this.netAmountPerShare = tax.netPerShare();
        this.netAmount = tax.net();
        this.taxCheckedAt = LocalDateTime.now();
    }

    /**
     * Ja recebido em {@code today} (fuso da B3, {@link DividendCalendar}):
     * pagamento ate hoje, contando o proprio dia (D0).
     *
     * <p>Sem data de pagamento: recebido se veio so do historico longo da B3
     * ({@link CashEventSource#B3_HISTORICO}), que lista apenas proventos
     * passados e nunca traz a data; a receber se veio da fonte recente com
     * pagamento "a definir". MANUAL (sem evento) conta como recebido.</p>
     *
     * <p>A mesma regra esta nas somas do {@link DividendRepository} — o
     * DividendRepositoryTest confere que as duas batem.</p>
     */
    public boolean isReceived(java.time.LocalDate today) {
        if (paymentDate != null) {
            return !paymentDate.isAfter(today);
        }
        return cashEvent == null || cashEvent.getSource() == CashEventSource.B3_HISTORICO;
    }

    /** Liquido quando ha regra; bruto quando nao ha (o dinheiro nao some da soma). */
    /**
     * Grava o provento de ativo em moeda estrangeira (docs/planos/PLANO_CADASTRO_MOEDAS.md, C6): bruto e líquido em
     * reais, o lançado na moeda, o câmbio e os tributos do exterior. Sem retenção brasileira.
     */
    public void applyForeign(ForeignDividend f) {
        this.currencyCode = f.currencyCode();
        this.fxRate = f.fxRate();
        this.amountPerShare = f.amountPerShare();
        this.originalAmountPerShare = f.originalAmountPerShare();
        this.originalTotalAmount = f.originalTotalAmount();
        this.totalAmount = f.gross();
        this.withholdingRule = null;
        this.withholdingRate = null;
        this.withholdingTax = null;
        this.foreignTaxRule = f.withholdingRule();
        this.foreignWithholdingRate = f.withholdingRule() != null ? f.withholdingRule().getRate() : null;
        this.foreignWithholdingTax = f.withholdingTax();
        this.foreignIncomeTax = f.incomeTax();
        this.netAmountPerShare = f.netPerShare();
        this.netAmount = f.net();
        this.taxCheckedAt = LocalDateTime.now();
    }

    private void clearForeign() {
        this.currencyCode = com.activest.domain.currency.Currency.BRL;
        this.fxRate = BigDecimal.ONE;
        this.originalAmountPerShare = null;
        this.originalTotalAmount = null;
        this.foreignTaxRule = null;
        this.foreignWithholdingRate = null;
        this.foreignWithholdingTax = null;
        this.foreignIncomeTax = null;
    }

    /** Resultado do cálculo do provento do exterior ({@link ForeignDividendCalculator}). */
    public record ForeignDividend(String currencyCode, BigDecimal fxRate, BigDecimal amountPerShare,
                                  BigDecimal originalAmountPerShare, BigDecimal originalTotalAmount, BigDecimal gross,
                                  com.activest.domain.currency.ForeignTaxRule withholdingRule,
                                  BigDecimal withholdingTax, BigDecimal incomeTax, BigDecimal netPerShare,
                                  BigDecimal net) {
    }

    public BigDecimal netOrGross() {
        return netAmount != null ? netAmount : totalAmount;
    }

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
