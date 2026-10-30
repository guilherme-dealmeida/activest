package com.activest.functional;

import com.activest.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listagem de emissoras (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 4 e 6): uma aba inteira por chamada, com os
 * mesmos valores de {@code GET /assets/{ticker}/indicators}, sem fracionarios, na ordem do PL decrescente e com as
 * marcas de dado antigo.
 */
class IssuerListingIT extends FunctionalBase {

    private static final List<String> TICKERS = List.of("PETR3", "PETR4", "VALE3", "HGLG11", "XPML11");

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private final Map<String, BigDecimal> pricesBefore = new HashMap<>();
    private User user;

    @BeforeEach
    void scenario() {
        user = userWithPortfolio("listagem@activest.test");
        for (String t : TICKERS) {
            pricesBefore.put(t, jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = ?",
                    BigDecimal.class, t));
        }
        jdbc.update("UPDATE assets SET current_price = NULL WHERE ticker IN ('PETR3', 'PETR4', 'VALE3', 'HGLG11', 'XPML11')");
        clearPrices();

        // PETR4 sem pregao ha 10 dias (marca); PETR3 com pregao de 2 dias atras
        price("PETR4", today.minusDays(10), "47.99");
        price("PETR3", today.minusDays(2), "51.20");
        price("VALE3", today.minusDays(1), "60.00");
        price("HGLG11", today.minusDays(1), "150.00");
        price("XPML11", today.minusDays(1), "100.00");
        dividend("PETR4", "DIVIDENDO", today.minusDays(30), "2.00");
        dividend("HGLG11", "RENDIMENTO", today.minusDays(20), "1.10");

        // Petrobras: balanco de 31/12/2025 (mais de 6 meses: marca); Vale: o do ultimo trimestre fechado, com PL
        // negativo de proposito (vem depois dos positivos e antes dos vazios)
        statement(issuer("PETR4"), LocalDate.of(2025, 12, 31), "110129000000", "415786000000", 12888732761L);
        statement(issuer("VALE3"), previousQuarterEnd(), "-5000000000", "-1000000000", 4268000000L);

        // HGLG11 com informe de 2 meses atras; XPML11 com informe de 5 meses atras (marca)
        report(issuer("HGLG11"), today.minusMonths(2).withDayOfMonth(1), "165.95", "7567668488.14");
        report(issuer("XPML11"), today.minusMonths(5).withDayOfMonth(1), "110.00", "7081112753.36");
    }

    @AfterEach
    void restores() {
        clearPrices();
        pricesBefore.forEach((t, p) -> jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = ?", p, t));
    }

    @Test
    @DisplayName("Aba Ações: sem fracionários, PL decrescente com desempate pelo ticker, vazios no fim")
    void stocksTab() {
        List<Map<String, Object>> rows = listing("COMPANY");
        List<String> tickers = rows.stream().map(l -> (String) l.get("ticker")).toList();

        assertThat(tickers).contains("PETR3", "PETR4", "VALE3").doesNotContain("PETR3F", "PETR4F", "VALE3F");
        assertThat(tickers.subList(0, 3)).containsExactly("PETR3", "PETR4", "VALE3");
        assertThat(rows.subList(3, rows.size())).allMatch(l -> l.get("equity") == null);
        assertThat(tickers.subList(3, tickers.size())).isSorted();
        assertThat(rows).noneMatch(l -> "HGLG11".equals(l.get("ticker")));

        Map<String, Object> petr4 = rows.get(1);
        Map<String, Object> registry = jdbc.queryForMap("""
                SELECT a.name, i.trade_name, i.legal_name, i.b3_sector, i.b3_subsector, i.b3_segment
                FROM assets a JOIN issuers i ON i.id = a.issuer_id WHERE a.ticker = 'PETR4'""");
        Object session = registry.get("trade_name");
        // F1: nome de pregao; sem ele, o nome do ativo
        assertThat(petr4.get("name")).isEqualTo(session == null || session.toString().isBlank()
                ? registry.get("name") : session);
        assertThat(petr4.get("legalName")).isEqualTo(registry.get("legal_name"));
        assertThat(petr4.get("sector")).isEqualTo(registry.get("b3_sector"));
        assertThat(petr4.get("subsector")).isEqualTo(registry.get("b3_subsector"));
        assertThat(petr4.get("segment")).isEqualTo(registry.get("b3_segment"));
        assertThat(number(petr4.get("equity"))).isEqualByComparingTo("415786000000");
    }

    @Test
    @DisplayName("Os valores da lista são os da página da emissora; a linha leva só os indicadores das colunas (T11)")
    @SuppressWarnings("unchecked")
    void sameValuesAsIssuer() {
        Map<String, Map<String, Object>> shares = byTicker(listing("COMPANY"));
        Map<String, Map<String, Object>> fiis = byTicker(listing("FII"));
        List<String> stockColumns = List.of("LPA", "VPA", "PL", "PVP", "ROE", "PAYOUT", "DY");
        List<String> fiiColumns = List.of("PVP", "DY", "VP_COTA", "COTISTAS", "IMOVEIS", "PAPEL", "ALAVANCAGEM");
        for (String t : TICKERS) {
            boolean fii = t.endsWith("11");
            Map<String, Object> row = fii ? fiis.get(t) : shares.get(t);
            Map<String, Object> page = current(t);
            List<Map<String, Object>> listed = (List<Map<String, Object>>) row.get("indicators");
            List<String> columns = fii ? fiiColumns : stockColumns;
            assertThat(listed).as(t).extracting(i -> i.get("indicator")).containsExactlyElementsOf(columns);
            assertThat(listed).as(t).isEqualTo(((List<Map<String, Object>>) page.get("indicators")).stream()
                    .filter(i -> columns.contains(i.get("indicator"))).toList());
            assertThat(row.get("price")).as(t).isEqualTo(page.get("price"));
            assertThat(row.get("basePeriodEnd")).as(t).isEqualTo(page.get("basePeriodEnd"));
        }
    }

    @Test
    @DisplayName("Fracionário sem o lote padrão no catálogo (EKTR3F): fica na lista, calculado no lote, com os valores da página do ticker")
    @SuppressWarnings("unchecked")
    void fractionalOnly() {
        // docs/planos/PLANO_PERFORMANCE.md, P2: o lote padrao nao existe no catalogo
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE upper(ticker) = 'EKTR3'", Integer.class))
                .isZero();
        BigDecimal before = jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = 'EKTR3F'",
                BigDecimal.class);
        jdbc.update("UPDATE assets SET current_price = NULL WHERE ticker = 'EKTR3F'");
        try {
            price("EKTR3F", today.minusDays(3), "38.50");
            dividend("EKTR3F", "DIVIDENDO", today.minusDays(40), "0.75");
            statement(issuer("EKTR3F"), previousQuarterEnd(), "900000000", "4000000000", 190000000L);

            Map<String, Object> row = byTicker(listing("COMPANY")).get("EKTR3F");
            Map<String, Object> page = current("EKTR3F");
            List<String> columns = List.of("LPA", "VPA", "PL", "PVP", "ROE", "PAYOUT", "DY");

            assertThat(row).isNotNull();
            assertThat(number(row.get("price"))).isEqualByComparingTo("38.50");
            assertThat(row.get("priceDate")).isEqualTo(today.minusDays(3).toString());
            assertThat(row.get("price")).isEqualTo(page.get("price"));
            assertThat(row.get("basePeriodEnd")).isEqualTo(page.get("basePeriodEnd"));
            assertThat(number(row.get("equity"))).isEqualByComparingTo("4000000000");
            assertThat((List<Map<String, Object>>) row.get("indicators"))
                    .isEqualTo(((List<Map<String, Object>>) page.get("indicators")).stream()
                            .filter(i -> columns.contains(i.get("indicator"))).toList());
            assertThat((List<Map<String, Object>>) row.get("indicators"))
                    .filteredOn(i -> "DY".equals(i.get("indicator")))
                    .allMatch(i -> i.get("value") != null);
        } finally {
            jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker = 'EKTR3F')");
            jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = 'EKTR3F'", before);
        }
    }

    @Test
    @DisplayName("Marca de dado antigo: preço há mais de 7 dias, balanço há mais de 6 meses, informe há mais de 3")
    void staleData() {
        Map<String, Map<String, Object>> shares = byTicker(listing("COMPANY"));
        assertThat(shares.get("PETR4").get("priceStale")).isEqualTo(true);
        assertThat(shares.get("PETR4").get("priceDate")).isEqualTo(today.minusDays(10).toString());
        assertThat(shares.get("PETR3").get("priceStale")).isEqualTo(false);
        assertThat(shares.get("PETR4").get("baseStale")).isEqualTo(true);
        assertThat(shares.get("VALE3").get("baseStale")).isEqualTo(false);

        Map<String, Map<String, Object>> fiis = byTicker(listing("FII"));
        assertThat(fiis.get("HGLG11").get("baseStale")).isEqualTo(false);
        assertThat(fiis.get("XPML11").get("baseStale")).isEqualTo(true);
    }

    @Test
    @DisplayName("Aba FIIs: PL do informe, segmento do fundo e nome do ativo")
    void abaFiis() {
        List<Map<String, Object>> rows = listing("FII");
        assertThat(rows.subList(0, 2)).extracting(l -> l.get("ticker")).containsExactly("HGLG11", "XPML11");
        assertThat(rows).noneMatch(l -> "PETR4".equals(l.get("ticker")));

        Map<String, Object> hglg = rows.get(0);
        Map<String, Object> registry = jdbc.queryForMap("""
                SELECT a.name, i.legal_name, i.segment FROM assets a JOIN issuers i ON i.id = a.issuer_id
                WHERE a.ticker = 'HGLG11'""");
        assertThat(hglg.get("name")).isEqualTo(registry.get("name"));
        assertThat(hglg.get("legalName")).isEqualTo(registry.get("legal_name"));
        assertThat(hglg.get("segment")).isEqualTo(registry.get("segment"));
        assertThat(hglg.get("sector")).isNull();
        assertThat(number(hglg.get("equity"))).isEqualByComparingTo("7567668488.14");
    }

    @Test
    @DisplayName("Sem login: 401; tipo inválido: 400")
    void access() {
        assertThat(rest.getForEntity("/api/v1/assets/indicators?kind=FII", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(user, "/api/v1/assets/indicators?kind=ETF", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ apoio

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listing(String kind) {
        ResponseEntity<List> r = get(user, "/api/v1/assets/indicators?kind=" + kind, List.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> current(String ticker) {
        ResponseEntity<Map> r = get(user, "/api/v1/assets/" + ticker + "/indicators", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private static Map<String, Map<String, Object>> byTicker(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        rows.forEach(l -> out.put((String) l.get("ticker"), l));
        return out;
    }

    private LocalDate previousQuarterEnd() {
        LocalDate quarterStart = today.withDayOfMonth(1).minusMonths((today.getMonthValue() - 1) % 3);
        return quarterStart.minusDays(1);
    }

    private long issuer(String ticker) {
        return jdbc.queryForObject("SELECT issuer_id FROM assets WHERE ticker = ?", Long.class, ticker);
    }

    private void clearPrices() {
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker IN "
                + "('PETR3', 'PETR4', 'VALE3', 'HGLG11', 'XPML11'))");
    }

    private void price(String ticker, LocalDate data, String closing) {
        jdbc.update("""
                INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
                SELECT id, ?, ?, 'B3_COTAHIST' FROM assets WHERE ticker = ?""",
                java.sql.Date.valueOf(data), new BigDecimal(closing), ticker);
    }

    private void dividend(String ticker, String type, LocalDate dataCom, String value) {
        jdbc.update("""
                INSERT INTO asset_cash_events (asset_id, type, label, data_com, value_per_share, source)
                SELECT id, ?, ?, ?, ?, 'B3' FROM assets WHERE ticker = ?""",
                type, type, java.sql.Date.valueOf(dataCom), new BigDecimal(value), ticker);
    }

    private void statement(long issuerId, LocalDate end, String earnings12m, String pl, long shares) {
        Long doc = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated)
                VALUES (?, 'ITR', ?, 1, true) RETURNING id""", Long.class, issuerId, java.sql.Date.valueOf(end));
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, source_doc_id, net_income_parent_ttm, equity_parent,
                                              shares_outstanding, dividends_paid_ttm)
                VALUES (?, ?, ?, ?, ?, ?, 0)""",
                issuerId, java.sql.Date.valueOf(end), doc, new BigDecimal(earnings12m), new BigDecimal(pl), shares);
    }

    private void report(long issuerId, LocalDate mes, String bookValuePerShare, String pl) {
        jdbc.update("""
                INSERT INTO fii_monthly (issuer_id, reference_month, version, nav_per_share, shareholders,
                                         real_estate_total, real_estate_companies, receivables_total, total_invested,
                                         liabilities_total, total_assets, net_assets)
                VALUES (?, ?, 1, ?, 600000, 7057544916.78, 1051691815.4, 31703049.55, 8289415918.47,
                        1282893011.09, 8850561499.23, ?)""",
                issuerId, java.sql.Date.valueOf(mes), new BigDecimal(bookValuePerShare), new BigDecimal(pl));
    }
}
