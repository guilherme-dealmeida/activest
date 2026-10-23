package com.activest.functional;

import com.activest.domain.user.User;
import com.activest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Desdobramento, grupamento e bonificacao pela API, do jeito que a tela ve
 * (docs/planos/PLANO_BRAPI.md, F6). O evento e gravado no catalogo; as transacoes do
 * usuario nunca sao reescritas.
 */
class CorporateEventsIT extends FunctionalBase {

    private User owner;

    @BeforeEach
    void userEntity() {
        owner = userWithPortfolio("eventos@activest.test");
    }

    private void event(String ticker, String type, String dataCom, String factor) {
        jdbc.update("""
            INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source)
            SELECT id, ?, CAST(? AS DATE), CAST(? AS NUMERIC), 'B3' FROM assets WHERE ticker = ?""",
            type, dataCom, factor, ticker);
    }

    @Test
    @DisplayName("Desdobramento 1:2: dobra as cotas, mantem o custo, e a venda na quantidade nova e aceita")
    void splitOneForTwo_doublesSharesAndKeepsCost() {
        event(CatalogIds.STOCK, "DESDOBRAMENTO", "2026-04-15", "100");
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "100", "30.00"));

        // A nota de corretagem depois do evento ja vem na quantidade nova.
        recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-05-04", "150", "16.00"));

        Map<String, Object> position = positionOf(owner, CatalogIds.STOCK);
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("50");
        assertThat(number(position.get("averagePrice"))).isEqualByComparingTo("15.0000");
        assertThat(number(position.get("totalInvested"))).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Sem o evento, a mesma venda seria recusada por falta de saldo (regra B)")
    void withoutEventSaleRejected() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "100", "30.00"));

        assertThat(recordExpectingError(owner, sale(CatalogIds.STOCK, "2026-05-04", "150", "16.00"))
            .getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("Bonificacao sem custo informado (B27): cotas novas na hora, com custo zero provisorio")
    void bonusWithoutCost_entersWithProvisionalZeroCost() {
        event(CatalogIds.STOCK, "BONIFICACAO", "2026-05-29", "10");
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "100", "20.00"));

        Map<String, Object> position = positionOf(owner, CatalogIds.STOCK);
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("110");
        assertThat(number(position.get("totalInvested"))).isEqualByComparingTo("2000.00");
        assertThat(number(position.get("averagePrice"))).isEqualByComparingTo("18.18181818");
    }

    @Test
    @DisplayName("Grupamento 10:1 com fracao: sobra a parte inteira e a fracao sai do custo")
    void reverseSplitWithFraction() {
        event(CatalogIds.STOCK, "GRUPAMENTO", "2026-04-15", "0.1");
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "105", "20.00"));

        Map<String, Object> position = positionOf(owner, CatalogIds.STOCK);
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("10");
        assertThat(number(position.get("totalInvested"))).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Provento com data-com depois do desdobramento e pago sobre as cotas novas")
    @SuppressWarnings("unchecked")
    void dividendAfterSplit() {
        event(CatalogIds.STOCK, "DESDOBRAMENTO", "2026-04-15", "100");
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
            SELECT id, 'DIVIDENDO', 'DIVIDENDO', DATE '2026-06-30', DATE '2026-07-20', 0.50, 'B3'
            FROM assets WHERE ticker = ?""", CatalogIds.STOCK);

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "100", "30.00"));

        Map<String, Object> summary = get(owner, "/api/v1/dividends/", Map.class).getBody();
        List<Map<String, Object>> recent = (List<Map<String, Object>>) summary.get("recent");
        assertThat(recent).singleElement()
            .satisfies(p -> assertThat(number(p.get("totalAmount"))).isEqualByComparingTo("100.00"));
    }
}
