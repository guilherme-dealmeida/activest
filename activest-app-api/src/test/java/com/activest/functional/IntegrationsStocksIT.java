package com.activest.functional;

import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.user.User;
import com.activest.support.BinanceFake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binance Stocks pela API (docs/planos/PLANO_INTEGRACOES.md, 10.3): a ordem do ETF ficticio ZZETF paga em USDC vira a
 * compra do ETF em USD (PTAX venda da data, D10) e a venda de USDC (D6); a conferencia de saldo compara a posicao do
 * ETF com a Funding (D8) na escala gravada.
 */
class IntegrationsStocksIT extends FunctionalBase {

    private static final String BASE = "/api/v1/integrations/connections";
    private static final String ETF = "ZZETF";

    @Autowired
    private ExchangeRateService exchangeRates;

    private User ana;
    private Object connection;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void scenario() {
        jdbc.update("DELETE FROM exchange_rates WHERE currency_code = 'USD' AND reference_date BETWEEN ? AND ?",
            Date.valueOf(LocalDate.of(2026, 6, 1)), Date.valueOf(LocalDate.of(2026, 6, 10)));
        jdbc.update("INSERT INTO exchange_rates (currency_code, reference_date, buy_rate, sell_rate) VALUES "
            + "('USD', ?, 4.9900, 5.0000), ('USD', ?, 5.4300, 5.4321)",
            Date.valueOf(LocalDate.of(2026, 6, 1)), Date.valueOf(LocalDate.of(2026, 6, 10)));
        exchangeRates.evict();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = ?", Long.class, ETF) == 0) {
            jdbc.update("INSERT INTO assets (ticker, name, asset_type_id, currency_code, active) VALUES "
                + "(?, 'ETF fictício', (SELECT id FROM asset_types WHERE name = 'ETF_INTERNACIONAL'), 'USD', true)", ETF);
        }
        ana = userWithPortfolio("stocks-" + System.nanoTime() + "@activest.test");
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        BinanceFake.stocks(binanceClient);
        Map<String, Object> body = new HashMap<>();
        body.put("provider", "BINANCE");
        body.put("label", "Binance");
        body.put("apiKey", "chave-ficticia-de-teste-AB12");
        body.put("apiSecret", "segredo-ficticio-de-teste");
        body.put("consentAccepted", true);
        connection = post(ana, BASE, body, Map.class).getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> item(Map<String, Object> run, String externalId) {
        return ((List<Map<String, Object>>) run.get("items")).stream()
            .filter(i -> externalId.equals(i.get("externalId"))).findFirst()
            .orElseThrow(() -> new AssertionError("item ausente: " + externalId));
    }

    @Test
    @DisplayName("Pre-visualizacao: ETF em USD com taxa no custo e venda de USDC pela PTAX venda da data")
    @SuppressWarnings("unchecked")
    void preview() {
        Map<String, Object> run = sync(ana, connection);
        assertThat(run.get("status")).isEqualTo("AWAITING_CONFIRMATION");

        Map<String, Object> etf = item(run, "binance:equity:E1");
        assertThat(etf.get("outcome")).isEqualTo("NEW");
        Map<String, Object> t = (Map<String, Object>) etf.get("transaction");
        assertThat(t.get("ticker")).isEqualTo(ETF);
        assertThat(t.get("currency")).isEqualTo("USD");
        assertThat(number(t.get("shares"))).isEqualByComparingTo("2.5");
        assertThat(number(t.get("pricePerShare"))).isEqualByComparingTo("100");
        assertThat(number(t.get("brokerageFee"))).isEqualByComparingTo("0.35");
        assertThat(t.get("transactionDate")).isEqualTo("2026-06-10");

        Map<String, Object> leg = item(run, "binance:equity:E1:quote");
        assertThat(leg.get("outcome")).isEqualTo("NEW");
        Map<String, Object> s = (Map<String, Object>) leg.get("transaction");
        assertThat(s.get("ticker")).isEqualTo("USDC");
        assertThat(s.get("type")).isEqualTo("SELL");
        assertThat(s.get("currency")).isEqualTo("BRL");
        assertThat(number(s.get("shares"))).isEqualByComparingTo("250.35");
        assertThat(number(s.get("pricePerShare"))).isEqualByComparingTo("5.4321");
    }

    @Test
    @DisplayName("Confirmar grava o ETF em USD com o cambio da data e a venda de USDC; o saldo da Funding nao diverge")
    @SuppressWarnings("unchecked")
    void confirm() {
        // O USDC que pagou o ETF (na conta real, o Convert BRL -> USDC).
        recordSuccessfully(ana, buy("USDC", "2026-06-01", "300", "5.00"));
        Map<String, Object> run = sync(ana, connection);

        Map<String, Object> body = new HashMap<>();
        body.put("duplicates", Map.of());
        ResponseEntity<Map> r = post(ana, BASE + "/" + connection + "/runs/" + run.get("runId") + "/confirm", body,
            Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> etf = jdbc.queryForMap("SELECT t.currency_code, t.fx_rate, t.original_price_per_share, "
            + "t.original_fee, t.shares, t.price_per_share FROM transactions t JOIN portfolio_assets pa ON pa.id = "
            + "t.portfolio_asset_id JOIN assets a ON a.id = pa.asset_id WHERE a.ticker = ? AND t.external_id = ?",
            ETF, "binance:equity:E1");
        assertThat(etf.get("currency_code")).isEqualTo("USD");
        assertThat((BigDecimal) etf.get("fx_rate")).isEqualByComparingTo("5.4321");
        assertThat((BigDecimal) etf.get("original_price_per_share")).isEqualByComparingTo("100");
        assertThat((BigDecimal) etf.get("original_fee")).isEqualByComparingTo("0.35");
        assertThat((BigDecimal) etf.get("shares")).isEqualByComparingTo("2.5");
        assertThat((BigDecimal) etf.get("price_per_share")).isEqualByComparingTo("543.21");

        BigDecimal usdc = jdbc.queryForObject("SELECT t.shares FROM transactions t WHERE t.external_id = ?",
            BigDecimal.class, "binance:equity:E1:quote");
        assertThat(usdc).isEqualByComparingTo("250.35");

        Map<String, Object> after = get(ana, BASE + "/" + connection + "/runs/" + run.get("runId"), Map.class).getBody();
        List<Map<String, Object>> divergences = (List<Map<String, Object>>) after.get("divergences");
        assertThat(divergences).extracting(d -> d.get("asset")).doesNotContain(ETF);
    }
}
