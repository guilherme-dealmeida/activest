package com.activest.domain.issuer.dto;

import com.activest.domain.issuer.CompanyQuarter;
import com.activest.domain.issuer.FiiMonthly;
import com.activest.domain.issuer.Issuer;
import com.activest.domain.issuer.IssuerKind;

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

    /**
     * Uma linha da serie trimestral ({@code company_quarters}): _q trimestre, _ttm 12 meses. Lucro bruto, EBITDA (EBIT +
     * depreciacao e amortizacao) e os circulantes desde a tela de Ativos (docs/planos/PLANO_TELA_ATIVOS.md, D13).
     */
    public record QuarterDTO(LocalDate periodEnd, LocalDate fiscalYearStart,
                             BigDecimal revenueQ, BigDecimal ebitQ, BigDecimal netIncomeQ, BigDecimal netIncomeParentQ,
                             BigDecimal fcoQ, BigDecimal dividendsDeclaredQ, BigDecimal dividendsPaidQ,
                             BigDecimal revenueTtm, BigDecimal ebitTtm, BigDecimal netIncomeTtm,
                             BigDecimal netIncomeParentTtm, BigDecimal fcoTtm, BigDecimal dividendsDeclaredTtm,
                             BigDecimal dividendsPaidTtm, BigDecimal equity, BigDecimal equityParent, BigDecimal totalAssets, BigDecimal cash,
                             BigDecimal grossDebt, Long sharesTotal, Long sharesOutstanding,
                             BigDecimal grossProfitQ, BigDecimal grossProfitTtm, BigDecimal ebitdaQ, BigDecimal ebitdaTtm,
                             BigDecimal currentAssets, BigDecimal currentLiabilities) {

        public static QuarterDTO of(CompanyQuarter q) {
            return new QuarterDTO(q.getPeriodEnd(), q.getFiscalYearStart(), q.getRevenueQ(), q.getEbitQ(),
                    q.getNetIncomeQ(), q.getNetIncomeParentQ(), q.getFcoQ(), q.getDividendsDeclaredQ(),
                    q.getDividendsPaidQ(), q.getRevenueTtm(), q.getEbitTtm(), q.getNetIncomeTtm(), q.getNetIncomeParentTtm(), q.getFcoTtm(),
                    q.getDividendsDeclaredTtm(), q.getDividendsPaidTtm(), q.getEquity(), q.getEquityParent(), q.getTotalAssets(), q.getCash(),
                    q.getGrossDebt(), q.getSharesTotal(), q.getSharesOutstanding(), q.getGrossProfitQ(), q.getGrossProfitTtm(),
                    sum(q.getEbitQ(), q.getDepreciationAmortizationQ()), sum(q.getEbitTtm(), q.getDepreciationAmortizationTtm()),
                    q.getCurrentAssets(), q.getCurrentLiabilities());
        }

        private static BigDecimal sum(BigDecimal a, BigDecimal b) {
            return a == null || b == null ? null : a.add(b);
        }
    }

    /**
     * Uma coluna do Anual dos Demonstrativos (docs/planos/PLANO_TELA_ATIVOS.md, D8 a D10): fluxos do exercicio (ou
     * acumulados do ano em andamento, {@code partial}) e a posicao do balanco da coluna.
     *
     * @param fiscalYear ano em que o exercicio termina (D9)
     * @param periodEnd  balanco da coluna (fim do exercicio, ou o ultimo no ano em andamento)
     * @param months     meses cobertos pelo fluxo (12 no fechado; 6 em "2026 · 6m")
     */
    public record YearDTO(int fiscalYear, LocalDate periodEnd, LocalDate fiscalYearStart, boolean partial, int months,
                          BigDecimal revenue, BigDecimal ebit, BigDecimal ebitda, BigDecimal grossProfit,
                          BigDecimal netIncome, BigDecimal netIncomeParent, BigDecimal fco, BigDecimal dividendsDeclared,
                          BigDecimal dividendsPaid, BigDecimal equity, BigDecimal equityParent, BigDecimal totalAssets,
                          BigDecimal currentAssets, BigDecimal currentLiabilities, BigDecimal cash, BigDecimal grossDebt,
                          Long sharesTotal, Long sharesOutstanding) {
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
