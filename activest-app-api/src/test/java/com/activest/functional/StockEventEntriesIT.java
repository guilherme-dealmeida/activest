package com.activest.functional;

import com.activest.domain.user.User;
import com.activest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eventos do papel no extrato de Transacoes (docs/planos/PLANO_EVENTOS_TRANSACOES.md): as linhas de
 * {@code GET /api/v1/transactions/stock-events} explicam a Carteira — compras, vendas e a variacao de cada evento
 * somam a quantidade da posicao.
 */
class StockEventEntriesIT extends FunctionalBase {

    private User owner;

    @BeforeEach
    void userEntity() {
        owner = userWithPortfolio("eventos-extrato@activest.test");
    }

    private Long event(String ticker, String type, String dataCom, String factor) {
        jdbc.update("""
            INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source)
            SELECT id, ?, CAST(? AS DATE), CAST(? AS NUMERIC), 'B3' FROM assets WHERE ticker = ?""",
            type, dataCom, factor, ticker);
        return jdbc.queryForObject("""
            SELECT e.id FROM asset_stock_events e JOIN assets a ON a.id = e.asset_id
            WHERE a.ticker = ? AND e.type = ? AND e.data_com = CAST(? AS DATE)""", Long.class, ticker, type, dataCom);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> entriesOf(User user) {
        return List.of(get(user, "/api/v1/transactions/stock-events", Map[].class).getBody())
            .stream().map(m -> (Map<String, Object>) m).toList();
    }

    @Test
    @DisplayName("Grupamento com fracao, desdobramento e bonificacao: uma linha por evento, e o extrato soma a Carteira")
    void ledgerMatchesPortfolio() {
        event(CatalogIds.STOCK, "BONIFICACAO", "1996-06-17", "20");          // antes da primeira compra: fora
        event(CatalogIds.STOCK, "GRUPAMENTO", "2026-04-15", "0.1");
        event(CatalogIds.STOCK, "DESDOBRAMENTO", "2026-06-15", "100");
        Long bonus = event(CatalogIds.STOCK, "BONIFICACAO", "2026-08-14", "10");

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "105", "20.00"));
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-05-04", "5", "190.00"));
        recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-07-01", "4", "100.00"));

        List<Map<String, Object>> entries = entriesOf(owner);
        assertThat(entries).extracting(e -> e.get("type"))
            .containsExactly("BONIFICACAO", "DESDOBRAMENTO", "GRUPAMENTO");

        Map<String, Object> b = entries.get(0);
        assertThat(b.get("stockEventId")).isEqualTo(bonus.intValue());
        assertThat(b.get("exDate")).isEqualTo("2026-08-17");
        assertThat(number(b.get("sharesBefore"))).isEqualByComparingTo("26");   // 15 x 2 - 4 vendidas
        assertThat(number(b.get("sharesDelta"))).isEqualByComparingTo("2");
        assertThat(number(b.get("fractionShares"))).isEqualByComparingTo("0.6");
        assertThat(b.get("taxUnitCost")).isNull();
        assertThat(b.get("taxCostDelta")).isNull();
        assertThat(b.get("taxCostOrigin")).isEqualTo("MISSING");
        assertThat(number(b.get("costDelta"))).isEqualByComparingTo("0");

        Map<String, Object> split = entries.get(1);
        assertThat(number(split.get("sharesBefore"))).isEqualByComparingTo("15");
        assertThat(number(split.get("sharesDelta"))).isEqualByComparingTo("15");
        assertThat(split.get("taxCostOrigin")).isNull();

        Map<String, Object> reverse = entries.get(2);
        assertThat(reverse.get("ticker")).isEqualTo(CatalogIds.STOCK);
        assertThat(reverse.get("dataCom")).isEqualTo("2026-04-15");
        assertThat(reverse.get("source")).isEqualTo("B3");
        assertThat(number(reverse.get("sharesBefore"))).isEqualByComparingTo("105");
        assertThat(number(reverse.get("sharesAfter"))).isEqualByComparingTo("10");
        assertThat(number(reverse.get("sharesDelta"))).isEqualByComparingTo("-95");
        assertThat(number(reverse.get("fractionShares"))).isEqualByComparingTo("0.5");
        assertThat(number(reverse.get("costDelta"))).isEqualByComparingTo("-100.00");

        BigDecimal ledger = new BigDecimal("106");                           // 105 + 5 - 4
        for (Map<String, Object> e : entries) {
            ledger = ledger.add(number(e.get("sharesDelta")));
        }
        Map<String, Object> position = positionOf(owner, CatalogIds.STOCK);
        assertThat(ledger).isEqualByComparingTo(number(position.get("totalShares"))).isEqualByComparingTo("28");
        assertThat(entries).allSatisfy(e -> assertThat(e.get("portfolioAssetId")).isEqualTo(position.get("id")));
    }

    @Test
    @DisplayName("Custo informado na bonificacao e o do IR: na linha (origem USER) e na Carteira (para o IR); o preco medio segue a corretora")
    void informedCost() {
        Long bonus = event(CatalogIds.STOCK, "BONIFICACAO", "2026-08-14", "1");
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "300", "3.50"));
        Map<String, Object> position = positionOf(owner, CatalogIds.STOCK);

        ResponseEntity<String> r = put(owner, "/api/v1/portfolio/assets/" + position.get("id") + "/stock-events/" + bonus
            + "/cost", Map.of("costPerShare", new BigDecimal("12.94564989")), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> e = entriesOf(owner).get(0);
        assertThat(number(e.get("sharesDelta"))).isEqualByComparingTo("3");
        assertThat(number(e.get("costDelta"))).isEqualByComparingTo("0");
        assertThat(number(e.get("taxUnitCost"))).isEqualByComparingTo("12.94564989");
        assertThat(number(e.get("taxCostDelta"))).isEqualByComparingTo("38.84");
        assertThat(e.get("taxCostOrigin")).isEqualTo("USER");

        // corretora: 1.050,00 / 303; IR: 1.050,00 + 38,84 da bonificacao
        Map<String, Object> after = positionOf(owner, CatalogIds.STOCK);
        assertThat(number(after.get("totalInvested"))).isEqualByComparingTo("1050.00");
        assertThat(number(after.get("taxInvested"))).isEqualByComparingTo("1088.84");
    }

    @Test
    @DisplayName("Os eventos sao do dono: outro usuario nao ve as linhas; sem token, 401")
    void scopedToOwner() {
        event(CatalogIds.STOCK, "DESDOBRAMENTO", "2026-06-15", "100");
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-03-02", "10", "20.00"));
        User other = userWithPortfolio("outro-extrato@activest.test");

        assertThat(entriesOf(owner)).hasSize(1);
        assertThat(entriesOf(other)).isEmpty();
        assertThat(rest.getForEntity("/api/v1/transactions/stock-events", String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
