package com.primevest.domain.issuer;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um mes do informe mensal de um FII na CVM (V34, F7), cru. Gravado pela adm-api
 * ({@code FiiMonthlyImportService}); so leitura por aqui. Significado de cada coluna na V34.
 * Taxas ({@code *Month}) sao fracao do mes: 0.0068 = 0,68%.
 */
@Entity
@Table(name = "fii_monthly")
@IdClass(FiiMonthly.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FiiMonthly {

    @Id
    @Column(name = "issuer_id")
    private Long issuerId;

    /** Primeiro dia do mes. */
    @Id
    @Column(name = "reference_month")
    private LocalDate referenceMonth;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "net_assets")
    private BigDecimal netAssets;
    @Column(name = "total_assets")
    private BigDecimal totalAssets;
    @Column(name = "shares_issued")
    private BigDecimal sharesIssued;
    @Column(name = "nav_per_share")
    private BigDecimal navPerShare;
    private Integer shareholders;

    @Column(name = "admin_fee_month")
    private BigDecimal adminFeeMonth;
    @Column(name = "dividend_yield_month")
    private BigDecimal dividendYieldMonth;
    @Column(name = "amortization_month")
    private BigDecimal amortizationMonth;

    @Column(name = "liquidity_total")
    private BigDecimal liquidityTotal;
    @Column(name = "total_invested")
    private BigDecimal totalInvested;
    @Column(name = "real_estate_total")
    private BigDecimal realEstateTotal;
    @Column(name = "real_estate_companies")
    private BigDecimal realEstateCompanies;
    @Column(name = "receivables_total")
    private BigDecimal receivablesTotal;
    @Column(name = "fii_shares_total")
    private BigDecimal fiiSharesTotal;
    @Column(name = "liabilities_total")
    private BigDecimal liabilitiesTotal;
    /** Rendimentos a distribuir; dividido pelas cotas da o rendimento estimado (so indicador). */
    @Column(name = "income_to_distribute")
    private BigDecimal incomeToDistribute;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long issuerId;
        private LocalDate referenceMonth;
    }
}
