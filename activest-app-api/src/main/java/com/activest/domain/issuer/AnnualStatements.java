package com.activest.domain.issuer;

import com.activest.domain.issuer.dto.IssuerDTOs.YearDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Demonstrativos no Anual (docs/planos/PLANO_TELA_ATIVOS.md, 2.5 "Anual", D8 e D9) a partir da serie trimestral —
 * funcao pura, sem banco.
 *
 * <ul>
 *   <li><b>Exercicio fechado</b> (linha do DFP): fluxo = 12 meses do exercicio (o anual do proprio DFP, o
 *       {@code _ttm} da linha); posicao = balanco do fim do exercicio.</li>
 *   <li><b>Ano em andamento</b> (ultima linha, ITR, depois do ultimo DFP): fluxo = soma dos trimestres isolados desde o
 *       inicio do exercicio (faltando um, nulo); posicao = ultimo balanco; {@code partial} e {@code months} para o
 *       "2026 · 6m".</li>
 *   <li><b>Ano da coluna</b> (D9): o ano em que o exercicio termina — marco/2025 a fevereiro/2026 = 2026.</li>
 * </ul>
 */
public final class AnnualStatements {

    private AnnualStatements() {
    }

    /** Uma linha da serie com o tipo do documento de origem. */
    public record Row(CompanyQuarter q, boolean annual) {
    }

    /** Linhas na ordem da data; resultado do exercicio mais antigo ao mais recente. */
    public static List<YearDTO> compute(List<Row> rows) {
        List<YearDTO> out = new ArrayList<>();
        for (Row r : rows) {
            if (r.annual()) {
                CompanyQuarter q = r.q();
                out.add(year(q, q.getPeriodEnd().getYear(), false, 12, q.getRevenueTtm(), q.getEbitTtm(),
                        ebitda(q.getEbitTtm(), q.getDepreciationAmortizationTtm()), q.getGrossProfitTtm(),
                        q.getNetIncomeTtm(), q.getNetIncomeParentTtm(), q.getFcoTtm(), q.getDividendsDeclaredTtm(),
                        q.getDividendsPaidTtm()));
            }
        }
        if (rows.isEmpty() || rows.get(rows.size() - 1).annual()) {
            return out;
        }
        // ano em andamento: os trimestres do mesmo exercicio da ultima linha
        CompanyQuarter last = rows.get(rows.size() - 1).q();
        LocalDate start = last.getFiscalYearStart();
        if (start == null) {
            return out;
        }
        List<CompanyQuarter> ytd = rows.stream().map(Row::q)
                .filter(q -> start.equals(q.getFiscalYearStart()) && !q.getPeriodEnd().isAfter(last.getPeriodEnd()))
                .toList();
        int months = (int) Math.round(ChronoUnit.DAYS.between(start, last.getPeriodEnd().plusDays(1)) / 30.44);
        int fiscalYear = start.plusYears(1).minusDays(1).getYear();
        BigDecimal d = sum(ytd, CompanyQuarter::getDepreciationAmortizationQ);
        BigDecimal ebit = sum(ytd, CompanyQuarter::getEbitQ);
        out.add(year(last, fiscalYear, true, months, sum(ytd, CompanyQuarter::getRevenueQ), ebit, ebitda(ebit, d),
                sum(ytd, CompanyQuarter::getGrossProfitQ), sum(ytd, CompanyQuarter::getNetIncomeQ),
                sum(ytd, CompanyQuarter::getNetIncomeParentQ), sum(ytd, CompanyQuarter::getFcoQ),
                sum(ytd, CompanyQuarter::getDividendsDeclaredQ), sum(ytd, CompanyQuarter::getDividendsPaidQ)));
        return out;
    }

    private static YearDTO year(CompanyQuarter p, int fiscalYear, boolean partial, int months, BigDecimal revenue,
                                BigDecimal ebit, BigDecimal ebitda, BigDecimal grossProfit, BigDecimal netIncome,
                                BigDecimal netIncomeParent, BigDecimal fco, BigDecimal dividendsDeclared,
                                BigDecimal dividendsPaid) {
        return new YearDTO(fiscalYear, p.getPeriodEnd(), p.getFiscalYearStart(), partial, months, revenue, ebit, ebitda,
                grossProfit, netIncome, netIncomeParent, fco, dividendsDeclared, dividendsPaid, p.getEquity(),
                p.getEquityParent(), p.getTotalAssets(), p.getCurrentAssets(), p.getCurrentLiabilities(), p.getCash(),
                p.getGrossDebt(), p.getSharesTotal(), p.getSharesOutstanding());
    }

    /** Soma dos trimestres isolados; faltando um, nulo (nao estima). */
    private static BigDecimal sum(List<CompanyQuarter> qs, Function<CompanyQuarter, BigDecimal> f) {
        BigDecimal total = BigDecimal.ZERO;
        for (CompanyQuarter q : qs) {
            BigDecimal v = f.apply(q);
            if (v == null) {
                return null;
            }
            total = total.add(v);
        }
        return total;
    }

    static BigDecimal ebitda(BigDecimal ebit, BigDecimal depreciation) {
        return ebit == null || depreciation == null ? null : ebit.add(depreciation);
    }
}
