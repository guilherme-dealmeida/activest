package com.activest.functional;

import com.activest.domain.dividend.DividendCalendar;
import com.activest.adm.domain.dividend.DividendCatalogService;
import com.activest.domain.user.User;
import com.activest.adm.integration.b3.B3Client;
import com.activest.adm.integration.b3.B3Fixtures;
import com.activest.adm.integration.b3.FnetClient;
import com.activest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proventos recebidos x a receber, de ponta a ponta. Recebido = pagamento ate
 * hoje (fuso da B3, contando o proprio dia); sem data de pagamento, conforme a
 * origem (Dividend.isReceived). A Carteira e o Dashboard so somam o recebido.
 *
 * <p>Hoje fixo em 24/09/2026 e a B3 gravada na mesma data: PETR4 tem JCP pago
 * em 21/09/2026 (recebido) e JCP/dividendo em 23/11 e 21/12/2026 (a receber).</p>
 */
class DividendsToReceiveIT extends CatalogE2EBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final String BBAS3 = "BBAS3";

    @MockBean private DividendCalendar dividendCalendar;

    @Autowired private DividendCatalogService dividendCatalogService;

    private User owner;

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("a-receber@activest.test");
        when(dividendCalendar.today()).thenReturn(TODAY);
        B3Client real = B3Fixtures.client();
        when(b3Client.companySupplement(anyString())).thenAnswer(i -> real.companySupplement(i.getArgument(0)));
        when(b3Client.fundSupplement(anyString())).thenAnswer(i -> real.fundSupplement(i.getArgument(0)));
        when(b3Client.companyDetail(anyString())).thenAnswer(i -> real.companyDetail(i.getArgument(0)));
        when(b3Client.cashDividendsHistory(anyString(), anyInt(), anyInt()))
            .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        jdbc.update("UPDATE assets SET isin = NULL");
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

    private Map<String, Object> dividendEvent(String ticker, String type, String payment) {
        List<Map<String, Object>> found = dividends().stream()
            .filter(p -> ticker.equals(p.get("ticker")) && type.equals(p.get("type")) && payment.equals(p.get("paymentDate")))
            .toList();
        assertThat(found).as("%s %s pago em %s", ticker, type, payment).isNotEmpty();
        return found.get(0);
    }

    private static boolean received(Map<String, Object> p) {
        return Boolean.TRUE.equals(p.get("received"));
    }

    private BigDecimal net(Map<String, Object> p) {
        Object v = p.get("netAmount") != null ? p.get("netAmount") : p.get("totalAmount");
        return number(v);
    }

    private BigDecimal sum(List<Map<String, Object>> list, Predicate<Map<String, Object>> filterParam, String field) {
        return list.stream().filter(filterParam).map(p -> p.get(field) == null ? BigDecimal.ZERO : number(p.get(field)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netSum(List<Map<String, Object>> list, Predicate<Map<String, Object>> filterParam) {
        return list.stream().filter(filterParam).map(this::net).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal portfolioDividends() {
        return number(((Map<String, Object>) get(owner, "/api/v1/portfolio/", Map.class).getBody()).get("totalDividends"));
    }

    private void petr4() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-05-04", "50", "38.00"));
        assertThat(dividendCatalogService.syncAll("teste").failures()).isZero();
    }

    private void event(String ticker, String type, String source, String dataCom, String payment, String value) {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
            SELECT id, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS NUMERIC), ?
            FROM assets WHERE ticker = ?""", type, type, dataCom, payment, value, source, ticker);
    }

    // ------------------------------------------------------------------
    // Cenarios
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PETR4 em 24/09/2026: JCP pago em 21/09 e recebido; os de 23/11 e 21/12 sao a receber")
    void statusByPayment() {
        petr4();

        assertThat(received(dividendEvent(CatalogIds.STOCK, "JCP", "2026-09-21"))).isTrue();
        assertThat(received(dividendEvent(CatalogIds.STOCK, "JCP", "2026-11-23"))).isFalse();
        assertThat(received(dividendEvent(CatalogIds.STOCK, "JCP", "2026-12-21"))).isFalse();
        assertThat(received(dividendEvent(CatalogIds.STOCK, "DIVIDENDO", "2026-12-21"))).isFalse();
    }

    @Test
    @DisplayName("D0: o provento pago hoje ja conta como recebido")
    void paidTodayIsReceived() {
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 21));
        petr4();

        assertThat(received(dividendEvent(CatalogIds.STOCK, "JCP", "2026-09-21"))).isTrue();
    }

    @Test
    @DisplayName("Virada do dia: o a receber passa a recebido sozinho, sem recalculo")
    void dayRollover() {
        event(BBAS3, "DIVIDENDO", "B3", "2026-09-10", "2026-09-25", "0.50");
        recordSuccessfully(owner, buy(BBAS3, "2026-09-01", "100", "22.00"));
        assertThat(received(dividends().get(0))).isFalse();
        assertThat(portfolioDividends()).isEqualByComparingTo("0");

        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 25));

        assertThat(received(dividends().get(0))).isTrue();
        assertThat(portfolioDividends()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Carteira e Dashboard ignoram o que ainda sera pago")
    void portfolioIgnoresToReceive() {
        petr4();
        List<Map<String, Object>> list = dividends();
        BigDecimal receivedNet = netSum(list, DividendsToReceiveIT::received);
        BigDecimal everything = netSum(list, p -> true);
        assertThat(receivedNet).isLessThan(everything);

        assertThat(number(positionOf(owner, CatalogIds.STOCK).get("totalDividends"))).isEqualByComparingTo(receivedNet);
        assertThat(portfolioDividends()).isEqualByComparingTo(receivedNet);
    }

    @Test
    @DisplayName("Posicao so com provento futuro (o caso TIMS3) mostra zero na Carteira")
    void onlyFutureDividend() {
        event(BBAS3, "JCP", "B3", "2026-09-22", "2027-01-22", "0.21668011");
        recordSuccessfully(owner, buy(BBAS3, "2026-09-01", "75", "22.00"));

        assertThat(dividends()).singleElement().satisfies(p -> assertThat(received(p)).isFalse());
        assertThat(number(positionOf(owner, BBAS3).get("totalDividends"))).isEqualByComparingTo("0");
        assertThat(portfolioDividends()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Resumo: recebido e a receber separados, e os dois somam tudo")
    void separateSummary() {
        petr4();
        Map<String, Object> summary = summary();
        List<Map<String, Object>> list = dividends();
        Predicate<Map<String, Object>> toReceive = p -> !received(p);

        assertThat(number(summary.get("totalDividends"))).isEqualByComparingTo(netSum(list, DividendsToReceiveIT::received));
        assertThat(number(summary.get("totalGross"))).isEqualByComparingTo(sum(list, DividendsToReceiveIT::received, "totalAmount"));
        assertThat(number(summary.get("totalTax"))).isEqualByComparingTo(sum(list, DividendsToReceiveIT::received, "withholdingTax"));
        assertThat(number(summary.get("totalToReceive"))).isEqualByComparingTo(netSum(list, toReceive)).isPositive();
        assertThat(number(summary.get("toReceiveGross"))).isEqualByComparingTo(sum(list, toReceive, "totalAmount"));
        assertThat(number(summary.get("toReceiveTax"))).isEqualByComparingTo(sum(list, toReceive, "withholdingTax"));
        assertThat(number(summary.get("totalDividends")).add(number(summary.get("totalToReceive"))))
            .isEqualByComparingTo(netSum(list, p -> true));
        @SuppressWarnings("unchecked")
        Map<String, Object> byType = (Map<String, Object>) summary.get("byType");
        assertThat(number(byType.get("JCP"))).isEqualByComparingTo(
            netSum(list, p -> received(p) && "JCP".equals(p.get("type"))));
    }

    @Test
    @DisplayName("Sem data de pagamento: recebido se veio do historico longo; a receber se veio da fonte recente")
    void withoutDateByOrigin() {
        event(BBAS3, "DIVIDENDO", "B3_HISTORICO", "2025-03-11", null, "0.10");
        event(BBAS3, "JCP", "B3", "2026-09-01", null, "0.20");
        recordSuccessfully(owner, buy(BBAS3, "2025-01-10", "100", "22.00"));

        assertThat(dividends()).hasSize(2).allSatisfy(p -> {
            if ("DIVIDENDO".equals(p.get("type"))) {
                assertThat(received(p)).as("historico longo, sem data").isTrue();
            } else {
                assertThat(received(p)).as("fonte recente, pagamento a definir").isFalse();
            }
        });
        assertThat(portfolioDividends()).isEqualByComparingTo("10.00");
        assertThat(number(summary().get("totalToReceive"))).isPositive();
    }

    @Test
    @DisplayName("Ultimos 12 meses (card Proventos do Dashboard): so o recebido com pagamento depois de hoje - 12 meses")
    void lastTwelveMonths() {
        event(BBAS3, "DIVIDENDO", "B3", "2025-06-02", "2025-06-10", "0.10");   // pago ha mais de 12 meses
        event(BBAS3, "DIVIDENDO", "B3", "2026-03-02", "2026-03-10", "0.30");   // pago dentro da janela
        event(BBAS3, "DIVIDENDO", "B3", "2026-09-10", "2026-12-10", "0.50");   // a receber
        recordSuccessfully(owner, buy(BBAS3, "2025-01-10", "100", "22.00"));

        Map<String, Object> r = summary();
        assertThat(number(r.get("totalDividends"))).isEqualByComparingTo("40.00");
        assertThat(number(r.get("last12Months"))).isEqualByComparingTo("30.00");
        assertThat(number(r.get("totalToReceive"))).isEqualByComparingTo("50.00");
    }
}
