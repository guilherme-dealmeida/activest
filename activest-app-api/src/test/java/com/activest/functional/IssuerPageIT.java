package com.activest.functional;

import com.activest.domain.user.User;
import org.junit.jupiter.api.AfterEach;
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
 * Backend da tela de emissoras (docs/planos/PLANO_TELA_EMISSORES.md, B1–B6): historico de todos os
 * indicadores numa chamada, emissor com logo/site/situacao, busca so com emissor, cotacao
 * historica ajustada e o issuerId nas telas que linkam.
 */
class IssuerPageIT extends FunctionalBase {

    static final String PETR = "33000167000101";

    private User user;

    @BeforeEach
    void scenario() {
        user = userWithPortfolio("emissoras@activest.test");
        clearPrices();
        price("PETR4", "2024-12-30", "35.00");
        price("PETR4", "2025-12-30", "30.00");
        price("PETR4", "2026-09-24", "47.50");
        price("PETR4", "2026-09-25", "47.99");
        // SBSP3: desdobramento 1:5 com data-com 02/04/2026
        price("SBSP3", "2026-04-01", "600.00");
        price("SBSP3", "2026-04-02", "610.00");
        price("SBSP3", "2026-04-06", "122.00");
        price("SBSP3", "2026-09-25", "130.00");
        jdbc.update("""
                INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source)
                SELECT id, 'DESDOBRAMENTO', DATE '2026-04-02', 400, 'B3' FROM assets WHERE ticker = 'SBSP3'""");
        long petr = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, PETR);
        Long doc = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated)
                VALUES (?, 'DFP', DATE '2025-12-31', 1, true) RETURNING id""", Long.class, petr);
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, source_doc_id, net_income_parent_ttm, equity_parent,
                                              shares_outstanding, dividends_paid_ttm)
                VALUES (?, DATE '2025-12-31', ?, 110129000000, 415786000000, 12888732761, 41236000000)""", petr, doc);
    }

    @AfterEach
    void restores() {
        clearPrices();
    }

    private void clearPrices() {
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN "
                + "(SELECT id FROM assets WHERE ticker IN ('PETR4', 'PETR3', 'SBSP3'))");
    }

    private void price(String ticker, String data, String closing) {
        jdbc.update("""
                INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
                SELECT id, CAST(? AS DATE), ?, 'B3_COTAHIST' FROM assets WHERE ticker = ?""",
                data, new BigDecimal(closing), ticker);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> quote(String ticker, String query) {
        ResponseEntity<Map> r = get(user, "/api/v1/assets/" + ticker + "/prices" + query, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> points(Map<String, Object> serie) {
        return (List<Map<String, Object>>) serie.get("points");
    }

    @Test
    @DisplayName("B4: todos os historicos da PETR4 numa chamada, na ordem do enum, iguais aos individuais")
    @SuppressWarnings("unchecked")
    void historyInBatch() {
        List<Map<String, Object>> all = get(user, "/api/v1/assets/PETR4/indicators/history", List.class).getBody();
        assertThat(all).extracting(h -> h.get("indicator"))
                .containsExactly("LPA", "VPA", "PL", "PVP", "ROE", "PAYOUT", "DY");
        Map<String, Object> lpa = get(user, "/api/v1/assets/PETR4/indicators/LPA/history", Map.class).getBody();
        assertThat(all.get(0)).isEqualTo(lpa);
        List<Map<String, Object>> fii = get(user, "/api/v1/assets/HGLG11/indicators/history", List.class).getBody();
        assertThat(fii).extracting(h -> h.get("indicator"))
                .containsExactly("PVP", "DY", "VP_COTA", "COTISTAS", "IMOVEIS", "PAPEL", "ALAVANCAGEM");
    }

    @Test
    @DisplayName("B1/B2: emissor com situacao, site e hasLogo por ticker")
    @SuppressWarnings("unchecked")
    void issuer() {
        long petr = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, PETR);
        Map<String, Object> e = get(user, "/api/v1/issuers/" + petr, Map.class).getBody();
        assertThat(e).containsKeys("registrationStatus", "website");
        List<Map<String, Object>> tickers = (List<Map<String, Object>>) e.get("tickers");
        assertThat(tickers).isNotEmpty().allSatisfy(t -> assertThat(t).containsKey("hasLogo"));
    }

    @Test
    @DisplayName("B3: busca com withIssuer so traz ativos ligados a emissor")
    @SuppressWarnings("unchecked")
    void searchWithIssuer() {
        List<Map<String, Object>> all = get(user, "/api/v1/assets/?query=BOVA", List.class).getBody();
        List<Map<String, Object>> bova = get(user, "/api/v1/assets/?query=BOVA&withIssuer=true", List.class).getBody();
        List<Map<String, Object>> withIssuer = get(user, "/api/v1/assets/?query=11&withIssuer=true", List.class).getBody();
        List<String> tickers = withIssuer.stream().map(a -> (String) a.get("ticker")).toList();
        assertThat(tickers).isNotEmpty().doesNotContain("BOVA11");
        for (String t : tickers) {
            assertThat(jdbc.queryForObject("SELECT issuer_id IS NOT NULL FROM assets WHERE ticker = ?", Boolean.class, t))
                    .as(t).isTrue();
        }
        assertThat(all.stream().map(a -> a.get("ticker"))).contains("BOVA11");
        assertThat(bova.stream().map(a -> a.get("ticker"))).doesNotContain("BOVA11");
    }

    @Test
    @DisplayName("Busca do Preco Teto: issuerKind=COMPANY so traz acoes (18/10/2026)")
    @SuppressWarnings("unchecked")
    void searchOnlyStocks() {
        List<Map<String, Object>> everything = get(user, "/api/v1/assets/?query=HGLG&withIssuer=true", List.class).getBody();
        List<Map<String, Object>> shares = get(user, "/api/v1/assets/?query=HGLG&withIssuer=true&issuerKind=COMPANY",
                List.class).getBody();
        List<Map<String, Object>> petr = get(user, "/api/v1/assets/?query=PETR&withIssuer=true&issuerKind=COMPANY",
                List.class).getBody();
        assertThat(everything.stream().map(a -> a.get("ticker"))).contains("HGLG11");
        assertThat(shares).isEmpty();
        assertThat(petr.stream().map(a -> a.get("ticker"))).contains("PETR4");
        assertThat(get(user, "/api/v1/assets/?query=PETR&withIssuer=true&issuerKind=XYZ", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("B5: 1D = fechamento anterior -> ultimo; MAX desde o primeiro pregao; periodo especifico; resumo")
    void quote() {
        Map<String, Object> d1 = quote("PETR4", "?range=1D");
        assertThat(points(d1)).extracting(p -> p.get("time")).containsExactly("2026-09-24", "2026-09-25");
        assertThat(d1.get("closeOnly")).isEqualTo(true);
        assertThat(number(d1.get("change"))).isEqualByComparingTo("0.49");
        assertThat(number(d1.get("changePct"))).isEqualByComparingTo("1.03");

        Map<String, Object> max = quote("PETR4", "?range=MAX");
        assertThat(max.get("firstDate")).isEqualTo("2024-12-30");
        assertThat(max.get("from")).isEqualTo("2024-12-30");
        assertThat(max.get("interval")).isEqualTo("WEEK");
        assertThat(number(max.get("min"))).isEqualByComparingTo("30");
        assertThat(number(max.get("max"))).isEqualByComparingTo("47.99");

        Map<String, Object> isDefault = quote("PETR4", "");
        assertThat(isDefault.get("range")).isEqualTo("1A");
        assertThat(points(isDefault)).extracting(p -> p.get("time")).containsExactly("2025-12-30", "2026-09-24", "2026-09-25");

        Map<String, Object> custom = quote("PETR4", "?from=2025-01-01&to=2025-12-31");
        assertThat(custom.get("range")).isEqualTo("CUSTOM");
        assertThat(points(custom)).extracting(p -> p.get("time")).containsExactly("2025-12-30");

        Map<String, Object> fracionario = quote("PETR4F", "?range=1D");
        assertThat(fracionario.get("priceTicker")).isEqualTo("PETR4");
    }

    @Test
    @DisplayName("B5/D9: SBSP3 ajustada pelo desdobramento — sem degrau de -80%")
    @SuppressWarnings("unchecked")
    void adjustedQuote() {
        Map<String, Object> s = quote("SBSP3", "?range=1A");
        assertThat(s.get("adjusted")).isEqualTo(true);
        Map<String, Object> first = points(s).get(0);
        assertThat(first.get("time")).isEqualTo("2026-04-01");
        assertThat(number(first.get("close"))).isEqualByComparingTo("120");
        assertThat(number(points(s).get(points(s).size() - 1).get("close"))).isEqualByComparingTo("130");
    }

    @Test
    @DisplayName("B5: erros — ticker inexistente 404, periodo invalido 400, datas invertidas 400; sem preco = serie vazia")
    void quoteErrors() {
        assertThat(get(user, "/api/v1/assets/XPTO9/prices", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(user, "/api/v1/assets/PETR4/prices?range=2A", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(user, "/api/v1/assets/PETR4/prices?from=2026-01-02&to=2025-01-01", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> empty = quote("PETR3", "?range=1A");
        assertThat(points(empty)).isEmpty();
        assertThat(empty.get("firstDate")).isNull();
    }

    @Test
    @DisplayName("B6: issuerId na carteira e no preco teto; nulo no ativo sem emissor")
    @SuppressWarnings("unchecked")
    void issuerIdOnScreens() {
        long petr = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, PETR);
        recordSuccessfully(user, buy("PETR4", "2026-09-01", "10", "40.00"));
        recordSuccessfully(user, buy("BOVA11", "2026-09-01", "1", "130.00"));
        assertThat(number(positionOf(user, "PETR4").get("issuerId")).longValue()).isEqualTo(petr);
        assertThat(positionOf(user, "BOVA11").get("issuerId")).isNull();

        // preco teto do modelo V2 (docs/planos/PLANO_PRECO_TETO.md): as entradas gravadas direto, sem passar pelos padroes
        java.util.UUID cp = jdbc.queryForObject("""
                INSERT INTO ceiling_prices (user_id, asset_id, shares, roe_pct, payout_pct, discount_rate_pct,
                    perpetual_growth_pct, safety_margin_pct, price, inputs_origin, calculated_at)
                SELECT ?, id, 1000, 20, 50, 13.75, 3, 15, 40, '{}'::jsonb, NOW() FROM assets WHERE ticker = 'PETR4'
                RETURNING id""", java.util.UUID.class, user.getId());
        for (int i = 1; i <= 4; i++) {
            jdbc.update("""
                    INSERT INTO ceiling_price_quarters (ceiling_price_id, position, period_end, original_value, unit,
                        currency, fx_rate, value_brl, origin)
                    VALUES (?, ?, CAST(? AS DATE), 100, 'UNIDADE', 'BRL', 1, 100, 'INFORMADO')""",
                    cp, i, "2025-0" + (i * 2) + "-28");
        }
        List<Map<String, Object>> ceilings = get(user, "/api/v1/ceiling-prices", List.class).getBody();
        assertThat(number(ceilings.get(0).get("issuerId")).longValue()).isEqualTo(petr);
    }
}
