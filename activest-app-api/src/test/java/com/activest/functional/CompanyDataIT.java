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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Card "Dados da empresa" / "Dados do fundo" (docs/planos/PLANO_TELA_ATIVOS.md, A3, D2 a D7):
 * {@code GET /api/v1/assets/{ticker}/company-data}. Contas da Petrobras do DFP 2025 real; ON 7.442.231.382 e
 * PN 5.446.501.379 (sem tesouraria).
 */
class CompanyDataIT extends FunctionalBase {

    static final String PETR = "33000167000101";
    static final String HGLG = "11728688000147";
    static final String TICKERS = "('PETR4', 'PETR3', 'HGLG11')";

    private User user;
    private Map<String, BigDecimal> pricesBefore;
    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void scenario() {
        user = userWithPortfolio("dados-empresa@activest.test");
        pricesBefore = new java.util.HashMap<>();
        jdbc.query("SELECT ticker, current_price FROM assets WHERE ticker IN " + TICKERS,
                rs -> { pricesBefore.put(rs.getString(1), rs.getBigDecimal(2)); });
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker IN " + TICKERS + ")");
        jdbc.update("UPDATE assets SET current_price = 47.99 WHERE ticker = 'PETR4'");
        jdbc.update("UPDATE assets SET current_price = 50.00 WHERE ticker = 'PETR3'");
        jdbc.update("UPDATE assets SET current_price = 147.86 WHERE ticker = 'HGLG11'");

        long petr = issuer(PETR);
        Long doc = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated)
                VALUES (?, 'DFP', DATE '2025-12-31', 1, true) RETURNING id""", Long.class, petr);
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, fiscal_year_start, source_doc_id, equity,
                    equity_parent, total_assets, cash, gross_debt, current_assets, current_liabilities, shares_total,
                    shares_outstanding)
                VALUES (?, DATE '2025-12-31', DATE '2025-01-01', ?, 417587000000, 415786000000, 1223389000000,
                        50608000000, 384025000000, 140026000000, 198368000000, 12888732761, 12888732761)""", petr, doc);
        jdbc.update("""
                INSERT INTO share_counts (document_id, source, ordinary_shares, preferred_shares, total_shares,
                                          ordinary_treasury, preferred_treasury, total_treasury, scale_factor, scale_status)
                VALUES (?, 'COMPOSICAO', 7442231382, 5446501379, 12888732761, 0, 0, 0, 1, 'OK')""", doc);

        // liquidez (D4): 3 pregoes na janela de 2 meses (media de quantidade x fechamento = 50.000) e 1 fora
        price("PETR4", today.minusDays(3), "40.00", 1000);
        price("PETR4", today.minusDays(2), "50.00", 1000);
        price("PETR4", today.minusDays(1), "60.00", 1000);
        price("PETR4", today.minusMonths(3), "10.00", 999_999);

        jdbc.update("""
                INSERT INTO fii_monthly (issuer_id, reference_month, version, nav_per_share, net_assets, shares_issued,
                                         total_assets)
                VALUES (?, DATE '2026-08-01', 1, 165.95, 8000000000.00, 48207463, 8850561499.23)""", issuer(HGLG));
    }

    @AfterEach
    void restores() {
        pricesBefore.forEach((t, p) -> jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = ?", p, t));
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker IN " + TICKERS + ")");
    }

    private long issuer(String cnpj) {
        return jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, cnpj);
    }

    private void price(String ticker, LocalDate date, String close, long volume) {
        jdbc.update("""
                INSERT INTO asset_price_history (asset_id, reference_date, close_price, volume, source)
                SELECT id, ?, ?, ?, 'B3_COTAHIST' FROM assets WHERE ticker = ?""",
                java.sql.Date.valueOf(date), new BigDecimal(close), volume, ticker);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(String ticker) {
        ResponseEntity<Map> r = get(user, "/api/v1/assets/" + ticker + "/company-data", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @Test
    @DisplayName("PETR4: valor de mercado ON (PETR3) + PN (PETR4), de firma, PL, papeis, ativos, dividas, caixa e liquidez")
    void petr4() {
        Map<String, Object> d = data("PETR4");
        assertThat(d.get("kind")).isEqualTo("COMPANY");
        assertThat(d.get("basePeriodEnd")).isEqualTo("2025-12-31");
        assertThat(d.get("baseStale")).isEqualTo(true);                                       // mais de 6 meses
        assertThat(d.get("priceStale")).isEqualTo(false);
        // 7.442.231.382 x 50,00 + 5.446.501.379 x 47,99
        assertThat(number(d.get("marketValue"))).isEqualByComparingTo("633489170278.21");
        assertThat(d.get("marketValueNote")).isNull();
        // + divida bruta 384.025 mi - caixa 50.608 mi
        assertThat(number(d.get("enterpriseValue"))).isEqualByComparingTo("966906170278.21");
        assertThat(number(d.get("netDebt"))).isEqualByComparingTo("333417000000");
        assertThat(number(d.get("equity"))).isEqualByComparingTo("415786000000");             // PL da controladora
        assertThat(number(d.get("sharesTotal"))).isEqualByComparingTo("12888732761");
        assertThat(number(d.get("totalAssets"))).isEqualByComparingTo("1223389000000");
        assertThat(number(d.get("currentAssets"))).isEqualByComparingTo("140026000000");
        assertThat(number(d.get("cash"))).isEqualByComparingTo("50608000000");
        assertThat(number(d.get("averageDailyLiquidity"))).isEqualByComparingTo("50000");
        assertThat(d.get("liquiditySessions")).isEqualTo(3);
        assertThat(d.get("liquidityMonths")).isEqualTo(2);
    }

    @Test
    @DisplayName("Classe sem cotacao (D3): ON avaliada pelo preco de PETR4, com a nota; o valor e o mesmo nos dois tickers")
    void classWithoutQuote() {
        jdbc.update("UPDATE assets SET current_price = NULL WHERE ticker = 'PETR3'");
        Map<String, Object> d = data("PETR4");
        assertThat(number(d.get("marketValue"))).isEqualByComparingTo("618530285200.39");   // 12.888.732.761 x 47,99
        assertThat(d.get("marketValueNote")).isEqualTo("ON sem negociacao: avaliada pelo preco de PETR4");
        assertThat(data("PETR3").get("marketValue")).isEqualTo(d.get("marketValue"));
    }

    @Test
    @DisplayName("HGLG11 (D6): valor de mercado = cotas x preco, PL e ativos do informe; sem dividas nem circulante")
    void fund() {
        Map<String, Object> d = data("HGLG11");
        assertThat(d.get("kind")).isEqualTo("FII");
        assertThat(d.get("basePeriodEnd")).isEqualTo("2026-08-31");
        assertThat(number(d.get("marketValue"))).isEqualByComparingTo("7127955479.18");      // 48.207.463 x 147,86
        assertThat(number(d.get("equity"))).isEqualByComparingTo("8000000000");
        assertThat(number(d.get("sharesTotal"))).isEqualByComparingTo("48207463");
        assertThat(number(d.get("totalAssets"))).isEqualByComparingTo("8850561499.23");
        assertThat(d.get("enterpriseValue")).isNull();
        assertThat(d.get("grossDebt")).isNull();
        assertThat(d.get("averageDailyLiquidity")).isNull();
        assertThat(d.get("liquiditySessions")).isEqualTo(0);
    }

    @Test
    @DisplayName("Ativo sem emissor: 404")
    void withoutIssuer() {
        assertThat(get(user, "/api/v1/assets/BOVA11/company-data", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
