package com.primevest.functional;

import com.primevest.domain.dividend.DividendCalendar;
import com.primevest.domain.dividend.DividendTaxVerifier;
import com.primevest.adm.domain.dividend.DividendCatalogService;
import com.primevest.domain.user.User;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3Fixtures;
import com.primevest.adm.integration.b3.FnetClient;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * IR retido na fonte sobre proventos, de ponta a ponta (docs/planos/PLANO_IR_PROVENTOS.md,
 * 5.2): uma regra por teste.
 *
 * <p>A B3 responde com os arquivos reais gravados em 24/09/2026 —
 * {@code company_PETR.json} traz exatamente os proventos de PETR4 conferidos no
 * extrato da B3 (secao 2 do plano). Com 50 cotas, o teste reproduz o extrato
 * centavo a centavo.</p>
 */
class DividendTaxIT extends CatalogE2EBase {

    private static final String BBAS3 = "BBAS3";
    private static final String ETF = "DIVD11";
    private static final String TEST_LAW = "Lei hipotetica (teste F12)";

    /** Hoje fixo: o dia da conferencia com o extrato. Recebido x a receber depende dele. */
    @MockBean private DividendCalendar dividendCalendar;

    @Autowired private DividendCatalogService dividendCatalogService;
    @Autowired private DividendTaxVerifier dividendTaxVerifier;

    private User owner;

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("ir-proventos@primevest.test");
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
        B3Client real = B3Fixtures.client();
        when(b3Client.companySupplement(anyString())).thenAnswer(i -> real.companySupplement(i.getArgument(0)));
        when(b3Client.fundSupplement(anyString())).thenAnswer(i -> real.fundSupplement(i.getArgument(0)));
        when(b3Client.companyDetail(anyString())).thenAnswer(i -> real.companyDetail(i.getArgument(0)));
        when(b3Client.cashDividendsHistory(anyString(), anyInt(), anyInt()))
            .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        jdbc.update("UPDATE assets SET isin = NULL");
    }

    /** Desfaz a "lei nova" do F12: as regras sao catalogo e valem para os outros testes. */
    @AfterEach
    void restoreRules() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM withholding_tax_rules WHERE legal_basis = ?", Integer.class,
                TEST_LAW) > 0) {
            jdbc.execute("TRUNCATE TABLE dividends");
            jdbc.update("DELETE FROM withholding_tax_rules WHERE legal_basis = ?", TEST_LAW);
            jdbc.update("UPDATE withholding_tax_rules SET valid_to = NULL WHERE dividend_type = 'JCP' AND valid_from = DATE '2026-01-01'");
        }
    }

    // ------------------------------------------------------------------
    // Atalhos
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> summary() {
        return get(owner, "/api/v1/dividends/", Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dividends() {
        return (List<Map<String, Object>>) summary().get("recent");
    }

    /** O provento do extrato: ativo, tipo e dia do pagamento. */
    private Map<String, Object> dividendEvent(String ticker, String type, String payment) {
        List<Map<String, Object>> found = dividends().stream()
            .filter(p -> ticker.equals(p.get("ticker")) && type.equals(p.get("type")) && payment.equals(p.get("paymentDate")))
            .toList();
        assertThat(found).as("%s %s pago em %s", ticker, type, payment).hasSize(1);
        return found.get(0);
    }

    private BigDecimal field(Map<String, Object> p, String name) {
        Object v = p.get(name);
        return v == null ? null : number(v);
    }

    private void checks(Map<String, Object> p, String gross, String aliquota, String ir, String net) {
        assertThat(field(p, "totalAmount")).as("bruto").isEqualByComparingTo(gross);
        assertThat(field(p, "withholdingRate")).as("aliquota").isEqualByComparingTo(aliquota);
        assertThat(field(p, "withholdingTax")).as("IR").isEqualByComparingTo(ir);
        assertThat(field(p, "netAmount")).as("liquido").isEqualByComparingTo(net);
    }

    /** PETR4, 50 cotas desde antes de dez/2025, e a rotina de proventos com a B3 gravada. */
    private void petr4InStatement() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2025-12-01", "50", "35.00"));
        DividendCatalogService.SyncResult r = dividendCatalogService.syncAll("teste");
        assertThat(r.failures()).isZero();
    }

    private void event(String ticker, String type, String approval, String dataCom, String payment, String value) {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, approved_on, data_com, payment_date, value_per_share, source)
            SELECT id, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS DATE), CAST(? AS NUMERIC), 'B3'
            FROM assets WHERE ticker = ?""", type, type, approval, dataCom, payment, value, ticker);
    }

    private Integer withoutTax() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dividends WHERE tax_checked_at IS NULL", Integer.class);
    }

    // ------------------------------------------------------------------
    // F1–F5: PETR4 igual ao extrato da B3
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F1 — JCP creditado em 2025 e pago em 20/02/2026 retem 15%: 20,05")
    void f1JcpCreditedIn2025() {
        petr4InStatement();

        Map<String, Object> p = dividendEvent(CatalogIds.STOCK, "JCP", "2026-02-20");
        checks(p, "23.58", "0.15", "3.53", "20.05");
        assertThat(field(p, "netAmountPerShare")).isEqualByComparingTo("0.40086321");
        assertThat(field(p, "amountPerShare")).isEqualByComparingTo("0.47160378");
    }

    @Test
    @DisplayName("F2 — JCP de 20/03/2026: bruto truncado 8,75 (nao 8,76) e liquido 7,44")
    void f2TruncatedGross() {
        petr4InStatement();

        checks(dividendEvent(CatalogIds.STOCK, "JCP", "2026-03-20"), "8.75", "0.15", "1.31", "7.44");
    }

    @Test
    @DisplayName("F3 — Dividendo de 20/03/2026 e isento: 14,82")
    void f3ExemptDividend() {
        petr4InStatement();

        checks(dividendEvent(CatalogIds.STOCK, "DIVIDENDO", "2026-03-20"), "14.82", "0", "0", "14.82");
    }

    @Test
    @DisplayName("F4 — JCP creditado em 2026 (pago em 22/06/2026) retem 17,5%: 12,92")
    void f4JcpCreditedIn2026() {
        petr4InStatement();

        Map<String, Object> p = dividendEvent(CatalogIds.STOCK, "JCP", "2026-06-22");
        checks(p, "15.65", "0.175", "2.73", "12.92");
        assertThat(p.get("withholdingLegalBasis").toString()).contains("LC 224/2025");
    }

    @Test
    @DisplayName("F5 — Rendimento de acao (atualizacao pela Selic) de 22/06/2026 retem 22,5%: 0,79")
    void f5StockIncome() {
        petr4InStatement();

        checks(dividendEvent(CatalogIds.STOCK, "RENDIMENTO", "2026-06-22"), "1.01", "0.225", "0.22", "0.79");
    }

    // ------------------------------------------------------------------
    // F6–F10: as outras regras
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F6 — BBAS3, 125 cotas, JCP de 11/06/2026 em duas parcelas: 8,41 + 6,16 = 14,57")
    void f6Bbas3() {
        event(BBAS3, "JCP", "2026-05-14", "2026-06-01", "2026-06-11", "0.08157785203");
        event(BBAS3, "JCP", "2026-05-14", "2026-06-01", "2026-06-11", "0.05968401166");

        recordSuccessfully(owner, buy(BBAS3, "2026-05-04", "125", "22.00"));

        List<Map<String, Object>> jcp = dividends().stream().filter(p -> BBAS3.equals(p.get("ticker"))).toList();
        assertThat(jcp).extracting(p -> field(p, "netAmount"))
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactlyInAnyOrder(new BigDecimal("8.41"), new BigDecimal("6.16"));
        assertThat(jcp.stream().map(p -> field(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("14.57");
    }

    @Test
    @DisplayName("F7 — Rendimento de FII e isento: TOTAL igual ao bruto")
    void f7FiiIncome() {
        event(CatalogIds.FII, "RENDIMENTO", "2026-08-29", "2026-08-29", "2026-09-15", "1.10");

        recordSuccessfully(owner, buy(CatalogIds.FII, "2026-08-01", "10", "160.00"));

        assertThat(dividends()).singleElement()
            .satisfies(p -> checks(p, "11.00", "0", "0", "11.00"));
    }

    @Test
    @DisplayName("F8 — Amortizacao de FII e isenta e continua reduzindo o custo")
    void f8Amortization() {
        event(CatalogIds.FII, "AMORTIZACAO", null, "2026-06-30", "2026-07-15", "1.00");

        recordSuccessfully(owner, buy(CatalogIds.FII, "2026-06-01", "100", "160.00"));

        assertThat(dividends()).singleElement()
            .satisfies(p -> checks(p, "100.00", "0", "0", "100.00"));
        assertThat(number(positionOf(owner, CatalogIds.FII).get("totalInvested"))).isEqualByComparingTo("15900.00");
    }

    @Test
    @DisplayName("F9 — Restituicao de capital de acao e isenta")
    void f9CapitalReturn() {
        event(CatalogIds.STOCK, "RESTITUICAO_CAPITAL", null, "2026-06-30", "2026-07-15", "0.50");

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-06-01", "10", "35.00"));

        assertThat(dividends()).singleElement()
            .satisfies(p -> checks(p, "5.00", "0", "0", "5.00"));
    }

    @Test
    @DisplayName("F10 — ETF nao tem regra: aliquota e liquido nulos, e a carteira soma o bruto")
    void f10WithoutRule() {
        event(ETF, "RENDIMENTO", "2026-09-04", "2026-09-04", "2026-09-15", "0.79830000000");

        recordSuccessfully(owner, buy(ETF, "2026-09-01", "10", "64.00"));

        assertThat(dividends()).singleElement().satisfies(p -> {
            assertThat(field(p, "totalAmount")).isEqualByComparingTo("7.98");
            assertThat(p.get("withholdingRate")).isNull();
            assertThat(p.get("withholdingTax")).isNull();
            assertThat(p.get("netAmountPerShare")).isNull();
            assertThat(p.get("netAmount")).isNull();
        });
        assertThat(withoutTax()).isZero();
        assertThat(number(positionOf(owner, ETF).get("totalDividends"))).isEqualByComparingTo("7.98");
    }

    // ------------------------------------------------------------------
    // F11–F13: proventos novos, lei nova e verificador
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F11 — Proventos novos (rotina e compra retroativa) nascem com o IR calculado")
    void f11NewDividends() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-08-01", "50", "40.00"));
        dividendCatalogService.syncAll("teste");
        int afterTheTask = dividends().size();
        assertThat(afterTheTask).isPositive();
        assertThat(withoutTax()).isZero();

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2025-12-01", "50", "35.00"));

        assertThat(dividends().size()).isGreaterThan(afterTheTask);
        assertThat(withoutTax()).isZero();
        checks(dividendEvent(CatalogIds.STOCK, "JCP", "2026-02-20"), "23.58", "0.15", "3.53", "20.05");
    }

    @Test
    @DisplayName("F12 — Lei nova por migration: so os JCP creditados na nova vigencia mudam")
    void f12NewLaw() {
        petr4InStatement();

        // O que uma migration de lei nova faz (cabecalho da V23).
        jdbc.update("UPDATE withholding_tax_rules SET valid_to = DATE '2026-06-30' WHERE dividend_type = 'JCP' AND valid_from = DATE '2026-01-01'");
        jdbc.update("""
            INSERT INTO withholding_tax_rules (dividend_type, asset_type_id, rate, base_date, valid_from, valid_to, legal_basis)
            SELECT 'JCP', id, 0.2000, 'CREDIT', DATE '2026-07-01', NULL, ? FROM asset_types WHERE name = 'ACAO'""",
            TEST_LAW);
        jdbc.update("UPDATE dividends SET tax_checked_at = NULL WHERE type = 'JCP'");

        assertThat(dividendTaxVerifier.verify()).isPositive();

        // Creditado em 06/08/2026: 20%. 0,67407131 x 50 = 33,70; IR 6,74.
        checks(dividendEvent(CatalogIds.STOCK, "JCP", "2026-11-23"), "33.70", "0.20", "6.74", "26.96");
        // Creditados antes da nova vigencia: como estavam.
        checks(dividendEvent(CatalogIds.STOCK, "JCP", "2026-06-22"), "15.65", "0.175", "2.73", "12.92");
        checks(dividendEvent(CatalogIds.STOCK, "JCP", "2026-02-20"), "23.58", "0.15", "3.53", "20.05");
    }

    @Test
    @DisplayName("F13 — Verificador na subida: proventos sem IR voltam preenchidos, com os mesmos valores")
    void f13Verifier() {
        petr4InStatement();
        Function<List<Map<String, Object>>, Map<Object, BigDecimal>> nets = list -> list.stream()
            .collect(Collectors.toMap(p -> p.get("id"), p -> field(p, "netAmount")));
        Map<Object, BigDecimal> before = nets.apply(dividends());

        jdbc.update("UPDATE dividends SET tax_checked_at = NULL");
        assertThat(withoutTax()).isPositive();

        assertThat(dividendTaxVerifier.verify()).isEqualTo(1);
        assertThat(withoutTax()).isZero();
        Map<Object, BigDecimal> after = nets.apply(dividends());
        assertThat(after.keySet()).isEqualTo(before.keySet());
        before.forEach((id, value) -> assertThat(after.get(id)).isEqualByComparingTo(value));
        assertThat(dividendTaxVerifier.verify()).isZero();
    }

    // ------------------------------------------------------------------
    // F14–F15: o que a tela le
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F14 — Carteira e Dashboard somam o provento liquido (so o ja recebido)")
    void f14PortfolioNet() {
        petr4InStatement();
        List<Map<String, Object>> received = dividends().stream().filter(p -> Boolean.TRUE.equals(p.get("received"))).toList();
        BigDecimal net = received.stream().map(p -> field(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = received.stream().map(p -> field(p, "totalAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net).isLessThan(gross);

        assertThat(number(positionOf(owner, CatalogIds.STOCK).get("totalDividends"))).isEqualByComparingTo(net);
        @SuppressWarnings("unchecked")
        Map<String, Object> portfolio = get(owner, "/api/v1/portfolio/", Map.class).getBody();
        assertThat(number(portfolio.get("totalDividends"))).isEqualByComparingTo(net);
    }

    @Test
    @DisplayName("F15 — Resumo da tela de proventos: totais e byType em liquido; bruto e IR a parte")
    void f15ScreenSummary() {
        petr4InStatement();
        Map<String, Object> summary = summary();
        List<Map<String, Object>> all = dividends();
        // Os totais do resumo sao do recebido; o a receber tem bloco proprio (ProventosAReceberIT).
        List<Map<String, Object>> list = all.stream().filter(p -> Boolean.TRUE.equals(p.get("received"))).toList();
        BigDecimal net = list.stream().map(p -> field(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = list.stream().map(p -> field(p, "totalAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ir = list.stream().map(p -> field(p, "withholdingTax")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal jcp = list.stream().filter(p -> "JCP".equals(p.get("type")))
            .map(p -> field(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(number(summary.get("totalDividends"))).isEqualByComparingTo(net);
        assertThat(number(summary.get("totalGross"))).isEqualByComparingTo(gross);
        assertThat(number(summary.get("totalTax"))).isEqualByComparingTo(ir);
        assertThat(gross.subtract(ir)).isEqualByComparingTo(net);
        @SuppressWarnings("unchecked")
        Map<String, Object> byType = (Map<String, Object>) summary.get("byType");
        assertThat(number(byType.get("JCP"))).isEqualByComparingTo(jcp);
        assertThat(all).allSatisfy(p -> {
            assertThat(p.get("amountPerShare")).isNotNull();
            assertThat(p.get("withholdingRate")).isNotNull();
            assertThat(p.get("netAmountPerShare")).isNotNull();
        });
    }
}
