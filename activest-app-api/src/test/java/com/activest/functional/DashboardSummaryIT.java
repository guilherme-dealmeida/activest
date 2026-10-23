package com.activest.functional;

import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.dividend.DividendCalendar;
import com.activest.domain.user.User;
import com.activest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Cards do Dashboard que saem do resumo da carteira ({@code GET /portfolio/}, escopo "Mudancas de tela", 17/10/2026):
 * Aportes (compras - o que as vendas devolveram, nos ultimos 12 meses) e o Valor liquido do Patrimonio (liquido
 * estimado na renda fixa e no Tesouro; nos demais tipos, o proprio valor atual). Os proventos de 12 meses estao em
 * {@link ProventosAReceberIT}.
 *
 * <p>Hoje fixo em 24/09/2026: a janela dos 12 meses vai de 24/09/2025 (exclusive) a 24/09/2026.</p>
 */
class DashboardSummaryIT extends FunctionalBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final String BBAS3 = "BBAS3";

    @MockBean private DividendCalendar dividendCalendar;

    private User owner;

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("dashboard@activest.test");
        when(dividendCalendar.today()).thenReturn(TODAY);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summary() {
        return get(owner, "/api/v1/portfolio/", Map.class).getBody();
    }

    /** CDI de 0,05% a.d. em cada dia util de {@code de} a {@code ate}, como no RendaFixaCurvaIT. */
    private void cdi(LocalDate de, LocalDate ate) {
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            if (BusinessCalendar.isBusinessDay(d)) {
                jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) "
                        + "SELECT id, ?, 0.05 FROM market_indices WHERE code = 'CDI' ON CONFLICT DO NOTHING", Date.valueOf(d));
            }
        }
    }

    @Test
    @DisplayName("Aportes: compras - vendas dos ultimos 12 meses; o que cai fora da janela (inclusive o dia de corte) nao entra")
    void netContributionsTwelveMonths() {
        recordSuccessfully(owner, buy(BBAS3, "2025-06-10", "100", "20.00"));   // fora da janela
        recordSuccessfully(owner, buy(BBAS3, "2025-09-24", "10", "20.00"));    // dia de corte: fora
        recordSuccessfully(owner, buy(BBAS3, "2026-03-10", "50", "22.00"));    // 1.100,00
        recordSuccessfully(owner, sale(BBAS3, "2026-05-11", "30", "25.00"));     // devolveu 750,00

        Map<String, Object> r = summary();
        assertThat(number(r.get("contributions12m"))).isEqualByComparingTo("350.00");
        assertThat(number(r.get("contributionsMonthlyAverage12m"))).isEqualByComparingTo("29.17");
    }

    @Test
    @DisplayName("Aportes negativos quando se resgatou mais do que se aplicou na janela")
    void negativeContributions() {
        recordSuccessfully(owner, buy(BBAS3, "2025-06-10", "100", "20.00"));
        recordSuccessfully(owner, sale(BBAS3, "2026-02-02", "60", "25.00"));

        assertThat(number(summary().get("contributions12m"))).isEqualByComparingTo("-1500.00");
    }

    @Test
    @DisplayName("Valor liquido: sem renda fixa nem Tesouro, e o proprio valor atual")
    void netOnlyEquities() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-10", "50", "38.00"));
        recordSuccessfully(owner, buy(BBAS3, "2026-03-10", "100", "22.00"));

        Map<String, Object> r = summary();
        assertThat(number(r.get("currentNetValue"))).isEqualByComparingTo(number(r.get("currentValue")));
    }

    @Test
    @DisplayName("Valor liquido: a renda fixa entra pelo liquido estimado da Carteira; o resto pelo valor atual")
    void netWithFixedIncome() {
        cdi(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));
        Map<String, Object> cdb = cdbInvestment(CatalogIds.CDB, "2024-01-02", "1000.00", "BANCO TESTE");
        cdb.put("indexerRate", new BigDecimal("100"));
        recordSuccessfully(owner, cdb);
        recordSuccessfully(owner, buy(BBAS3, "2026-03-10", "100", "22.00"));

        Map<String, Object> cdbRow = positionOf(owner, CatalogIds.CDB);
        BigDecimal cdbNet = number(cdbRow.get("estimatedNet"));
        assertThat(cdbNet).as("IR e IOF sobre o rendimento").isLessThan(number(cdbRow.get("currentValue")));

        BigDecimal expected = cdbNet.add(number(positionOf(owner, BBAS3).get("currentValue")));
        Map<String, Object> r = summary();
        assertThat(number(r.get("currentNetValue"))).isEqualByComparingTo(expected);
        assertThat(number(r.get("currentNetValue"))).isLessThan(number(r.get("currentValue")));
    }
}
