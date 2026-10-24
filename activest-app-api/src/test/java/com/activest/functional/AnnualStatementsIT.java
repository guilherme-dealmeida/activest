package com.activest.functional;

import com.activest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrativos no Anual (docs/planos/PLANO_TELA_ATIVOS.md, A3, D8 a D10): {@code GET /api/v1/issuers/{id}/years},
 * e as contas novas em {@code /quarters} (D13).
 */
class AnnualStatementsIT extends FunctionalBase {

    static final String PETR = "33000167000101";
    static final String CAMIL = "64904295000103";
    static final String HGLG = "11728688000147";

    private User user;

    @BeforeEach
    void scenario() {
        user = userWithPortfolio("anual@activest.test");
    }

    private long issuer(String cnpj) {
        return jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, cnpj);
    }

    /** Uma linha da serie: fluxo do trimestre (q) e de 12 meses (ttm) da receita, do EBIT e da D&A; posicao = PL. */
    private void row(long issuer, String type, String end, String fiscalStart, Long revenueQ, Long revenueTtm,
                     Long ebitQ, Long daQ, Long ebitTtm, Long daTtm, long equity) {
        Long doc = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated)
                VALUES (?, ?, CAST(? AS DATE), 1, true) RETURNING id""", Long.class, issuer, type, end);
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, fiscal_year_start, source_doc_id, revenue_q,
                    revenue_ttm, ebit_q, depreciation_amortization_q, ebit_ttm, depreciation_amortization_ttm, equity,
                    current_assets, gross_profit_q, gross_profit_ttm)
                VALUES (?, CAST(? AS DATE), CAST(? AS DATE), ?, ?, ?, ?, ?, ?, ?, ?, 500, 7, 70)""",
                issuer, end, fiscalStart, doc, revenueQ, revenueTtm, ebitQ, daQ, ebitTtm, daTtm, equity);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> years(long issuer) {
        var r = get(user, "/api/v1/issuers/" + issuer + "/years", List.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @Test
    @DisplayName("Ano civil: exercicios fechados pelo DFP e 2026 acumulado de jan a jun (2 ITRs, 6 meses)")
    void calendarYear() {
        long petr = issuer(PETR);
        row(petr, "DFP", "2024-12-31", "2024-01-01", 120L, 400L, 30L, 10L, 100L, 40L, 1000);
        row(petr, "DFP", "2025-12-31", "2025-01-01", 140L, 500L, 35L, 12L, 120L, 45L, 1100);
        row(petr, "ITR", "2026-03-31", "2026-01-01", 150L, 530L, 40L, 11L, 125L, 46L, 1150);
        row(petr, "ITR", "2026-06-30", "2026-01-01", 160L, 560L, 42L, 13L, 130L, 47L, 1200);

        List<Map<String, Object>> y = years(petr);
        assertThat(y).extracting(r -> r.get("fiscalYear")).containsExactly(2024, 2025, 2026);
        Map<String, Object> y2025 = y.get(1);
        assertThat(y2025.get("partial")).isEqualTo(false);
        assertThat(y2025.get("months")).isEqualTo(12);
        assertThat(number(y2025.get("revenue"))).isEqualByComparingTo("500");          // 12 meses do DFP
        assertThat(number(y2025.get("ebitda"))).isEqualByComparingTo("165");           // 120 + 45
        assertThat(number(y2025.get("equity"))).isEqualByComparingTo("1100");          // balanco do fim do exercicio

        Map<String, Object> y2026 = y.get(2);
        assertThat(y2026.get("partial")).isEqualTo(true);
        assertThat(y2026.get("months")).isEqualTo(6);
        assertThat(y2026.get("periodEnd")).isEqualTo("2026-06-30");
        assertThat(number(y2026.get("revenue"))).isEqualByComparingTo("310");          // 150 + 160, acumulado
        assertThat(number(y2026.get("ebitda"))).isEqualByComparingTo("106");           // (40 + 11) + (42 + 13)
        assertThat(number(y2026.get("grossProfit"))).isEqualByComparingTo("14");
        assertThat(number(y2026.get("equity"))).isEqualByComparingTo("1200");          // ultimo balanco
        assertThat(number(y2026.get("currentAssets"))).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Exercicio de marco a fevereiro (Camil, D9): a coluna e o ano em que termina; trimestre faltando = nulo")
    void fiscalYearOffCalendar() {
        long camil = issuer(CAMIL);
        row(camil, "DFP", "2025-02-28", "2024-03-01", 90L, 300L, 9L, 1L, 30L, 4L, 900);
        row(camil, "ITR", "2025-05-31", "2025-03-01", 80L, 310L, 8L, 1L, 31L, 4L, 910);
        row(camil, "ITR", "2025-08-31", "2025-03-01", null, null, null, null, null, null, 920);

        List<Map<String, Object>> y = years(camil);
        assertThat(y).extracting(r -> r.get("fiscalYear")).containsExactly(2025, 2026);
        assertThat(y.get(1).get("partial")).isEqualTo(true);
        assertThat(y.get(1).get("months")).isEqualTo(6);
        assertThat(y.get(1).get("revenue")).isNull();                                  // falta o 2T: nao estima
        assertThat(number(y.get(1).get("equity"))).isEqualByComparingTo("920");
    }

    @Test
    @DisplayName("Ultima linha e DFP: sem coluna parcial; trimestral ganha lucro bruto, EBITDA e circulantes (D13)")
    @SuppressWarnings("unchecked")
    void closedAndQuarters() {
        long petr = issuer(PETR);
        row(petr, "DFP", "2025-12-31", "2025-01-01", 140L, 500L, 35L, 12L, 120L, 45L, 1100);
        assertThat(years(petr)).singleElement().satisfies(r -> assertThat(r.get("partial")).isEqualTo(false));

        List<Map<String, Object>> q = get(user, "/api/v1/issuers/" + petr + "/quarters", List.class).getBody();
        assertThat(q).singleElement().satisfies(r -> {
            assertThat(number(r.get("ebitdaQ"))).isEqualByComparingTo("47");
            assertThat(number(r.get("ebitdaTtm"))).isEqualByComparingTo("165");
            assertThat(number(r.get("grossProfitTtm"))).isEqualByComparingTo("70");
            assertThat(number(r.get("currentAssets"))).isEqualByComparingTo("500");
            assertThat(r).doesNotContainKey("currentLiabilities");    // nulo nao vem no JSON
        });
    }

    @Test
    @DisplayName("FII: 400")
    void fund() {
        assertThat(get(user, "/api/v1/issuers/" + issuer(HGLG) + "/years", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
