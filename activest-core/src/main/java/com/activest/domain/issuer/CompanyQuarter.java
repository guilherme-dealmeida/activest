package com.activest.domain.issuer;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um trimestre de uma companhia (V33, F6): derivado dos balancos da CVM e recalculado inteiro a cada carga
 * ({@code CompanyQuarterService}); so leitura por aqui. {@code *Q} = trimestre isolado, {@code *Ttm} = 12
 * meses, o resto e a posicao na data. Regras na V33 e em {@code QuarterSeries}.
 */
@Entity
@Table(name = "company_quarters")
@IdClass(CompanyQuarter.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyQuarter {

    @Id
    @Column(name = "issuer_id")
    private Long issuerId;

    @Id
    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "fiscal_year_start")
    private LocalDate fiscalYearStart;

    @Column(name = "source_doc_id", nullable = false)
    private Long sourceDocId;

    @Column(name = "revenue_q")
    private BigDecimal revenueQ;
    @Column(name = "ebit_q")
    private BigDecimal ebitQ;
    @Column(name = "net_income_q")
    private BigDecimal netIncomeQ;
    @Column(name = "net_income_parent_q")
    private BigDecimal netIncomeParentQ;
    @Column(name = "fco_q")
    private BigDecimal fcoQ;
    /** Dividendos + JCP declarados (DVA). */
    @Column(name = "dividends_declared_q")
    private BigDecimal dividendsDeclaredQ;

    /** Dividendos + JCP pagos aos acionistas da controladora no trimestre (DFC) — base do payout (V45). */
    @Column(name = "dividends_paid_q")
    private BigDecimal dividendsPaidQ;

    @Column(name = "revenue_ttm")
    private BigDecimal revenueTtm;
    @Column(name = "ebit_ttm")
    private BigDecimal ebitTtm;
    @Column(name = "net_income_ttm")
    private BigDecimal netIncomeTtm;
    @Column(name = "net_income_parent_ttm")
    private BigDecimal netIncomeParentTtm;
    @Column(name = "fco_ttm")
    private BigDecimal fcoTtm;
    @Column(name = "dividends_declared_ttm")
    private BigDecimal dividendsDeclaredTtm;

    @Column(name = "dividends_paid_ttm")
    private BigDecimal dividendsPaidTtm;

    private BigDecimal equity;
    @Column(name = "equity_parent")
    private BigDecimal equityParent;
    @Column(name = "total_assets")
    private BigDecimal totalAssets;
    private BigDecimal cash;
    @Column(name = "gross_debt")
    private BigDecimal grossDebt;

    /** Em unidades; nulo quando a escala da quantidade esta em REVISAR ou nao ha quantidade. */
    @Column(name = "shares_total")
    private Long sharesTotal;
    @Column(name = "shares_outstanding")
    private Long sharesOutstanding;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long issuerId;
        private LocalDate periodEnd;
    }
}
