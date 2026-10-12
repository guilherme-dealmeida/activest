package com.primevest.domain.issuer.dto;

import com.primevest.domain.issuer.CompanyQuarter;
import com.primevest.domain.issuer.FiiMonthly;
import com.primevest.domain.issuer.Issuer;
import com.primevest.domain.issuer.IssuerKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Leituras do emissor para a tela (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.4 — F9). */
public final class IssuerDTOs {

    private IssuerDTOs() {
    }

    /** Cadastro do emissor e os tickers ligados. */
    public record IssuerDTO(Long id, IssuerKind kind, String cnpj, Integer cvmCode, String legalName,
                            String tradeName, String registrationStatus, String website,
                            String b3Sector, String b3Subsector, String b3Segment,
                            String segment, String listingSegment, Short fiscalYearEndMonth,
                            String administratorName, List<Ticker> tickers) {

        public static IssuerDTO of(Issuer i, List<Ticker> tickers) {
            return new IssuerDTO(i.getId(), i.getKind(), i.getCnpj(), i.getCvmCode(), i.getLegalName(),
                    i.getTradeName(), i.getRegistrationStatus(), i.getWebsite(), i.getB3Sector(), i.getB3Subsector(),
                    i.getB3Segment(), i.getSegment(), i.getListingSegment(), i.getFiscalYearEndMonth(),
                    i.getAdministratorName(), tickers);
        }
    }

    /** Ticker ligado ao emissor; {@code hasLogo}: existe arquivo em carga/data/logos (tela de emissoras, B1). */
    public record Ticker(String ticker, String shareClass, boolean active, boolean hasLogo) {
    }

    /** Uma linha da serie trimestral ({@code company_quarters}): _q trimestre, _ttm 12 meses. */
    public record QuarterDTO(LocalDate periodEnd, LocalDate fiscalYearStart,
                             BigDecimal revenueQ, BigDecimal ebitQ, BigDecimal netIncomeQ, BigDecimal netIncomeParentQ,
                             BigDecimal fcoQ, BigDecimal dividendsDeclaredQ, BigDecimal dividendsPaidQ,
                             BigDecimal revenueTtm, BigDecimal ebitTtm, BigDecimal netIncomeTtm,
                             BigDecimal netIncomeParentTtm, BigDecimal fcoTtm, BigDecimal dividendsDeclaredTtm,
                             BigDecimal dividendsPaidTtm, BigDecimal equity, BigDecimal equityParent, BigDecimal totalAssets, BigDecimal cash,
                             BigDecimal grossDebt, Long sharesTotal, Long sharesOutstanding) {

        public static QuarterDTO of(CompanyQuarter q) {
            return new QuarterDTO(q.getPeriodEnd(), q.getFiscalYearStart(), q.getRevenueQ(), q.getEbitQ(),
                    q.getNetIncomeQ(), q.getNetIncomeParentQ(), q.getFcoQ(), q.getDividendsDeclaredQ(),
                    q.getDividendsPaidQ(), q.getRevenueTtm(), q.getEbitTtm(), q.getNetIncomeTtm(), q.getNetIncomeParentTtm(), q.getFcoTtm(),
                    q.getDividendsDeclaredTtm(), q.getDividendsPaidTtm(), q.getEquity(), q.getEquityParent(), q.getTotalAssets(), q.getCash(),
                    q.getGrossDebt(), q.getSharesTotal(), q.getSharesOutstanding());
        }
    }

    /** Um mes do informe de FII ({@code fii_monthly}); taxas em fracao do mes. */
    public record MonthDTO(LocalDate referenceMonth, Integer version, BigDecimal netAssets, BigDecimal totalAssets,
                           BigDecimal sharesIssued, BigDecimal navPerShare, Integer shareholders,
                           BigDecimal adminFeeMonth, BigDecimal dividendYieldMonth, BigDecimal amortizationMonth,
                           BigDecimal liquidityTotal, BigDecimal totalInvested, BigDecimal realEstateTotal,
                           BigDecimal realEstateCompanies, BigDecimal receivablesTotal, BigDecimal fiiSharesTotal,
                           BigDecimal liabilitiesTotal, BigDecimal incomeToDistribute) {

        public static MonthDTO of(FiiMonthly m) {
            return new MonthDTO(m.getReferenceMonth(), m.getVersion(), m.getNetAssets(), m.getTotalAssets(),
                    m.getSharesIssued(), m.getNavPerShare(), m.getShareholders(), m.getAdminFeeMonth(),
                    m.getDividendYieldMonth(), m.getAmortizationMonth(), m.getLiquidityTotal(), m.getTotalInvested(),
                    m.getRealEstateTotal(), m.getRealEstateCompanies(), m.getReceivablesTotal(), m.getFiiSharesTotal(),
                    m.getLiabilitiesTotal(), m.getIncomeToDistribute());
        }
    }
}
