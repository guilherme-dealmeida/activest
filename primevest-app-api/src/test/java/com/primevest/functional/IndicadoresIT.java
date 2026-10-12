package com.primevest.functional;

import com.primevest.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F9 do docs/planos/PLANO_EMPRESAS_BALANCOS.md de ponta a ponta: balancos ({@code company_quarters}),
 * informe de FII ({@code fii_monthly}), proventos e precos no banco → endpoints da app-api.
 * Numeros da Petrobras dos DFP 2024 e 2025 reais (lucro e PL da controladora, acoes, DVA).
 */
class IndicadoresIT extends FuncionalBase {

    static final String PETR = "33000167000101";
    static final String HGLG = "11728688000147";

    private User user;
    private BigDecimal petr4PrecoAntes;
    private BigDecimal hglgPrecoAntes;
    private BigDecimal apxrPrecoAntes;
    private final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void cenario() {
        user = usuarioComCarteira("indicadores@primevest.test");
        petr4PrecoAntes = jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = 'PETR4'", BigDecimal.class);
        hglgPrecoAntes = jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = 'HGLG11'", BigDecimal.class);
        apxrPrecoAntes = jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = 'APXR11'", BigDecimal.class);
        jdbc.update("UPDATE assets SET current_price = 47.99 WHERE ticker = 'PETR4'");
        jdbc.update("UPDATE assets SET current_price = 147.86 WHERE ticker = 'HGLG11'");
        jdbc.update("UPDATE assets SET current_price = 95.00 WHERE ticker = 'APXR11'");
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN "
                + "(SELECT id FROM assets WHERE ticker IN ('PETR4', 'HGLG11', 'APXR11'))");

        // Petrobras: DFP 2024 e 2025
        long petr = issuer(PETR);
        documento(petr, "2024-12-31", "36606000000", "366006000000", "30000000000");
        documento(petr, "2025-12-31", "110129000000", "415786000000", "41236000000");
        preco("PETR4", "2024-12-30", "35.00");
        preco("PETR4", "2025-12-30", "30.00");
        provento("PETR4", "DIVIDENDO", LocalDate.of(2025, 6, 2), "1.00");
        provento("PETR4", "JCP", LocalDate.of(2025, 12, 22), "2.21");
        provento("PETR4", "DIVIDENDO", hoje.minusDays(30), "2.00");

        // HGLG11: informe de dez/2025 e ago/2026, 12 rendimentos de 1,10 em 2025
        long hglg = issuer(HGLG);
        mes(hglg, "2025-12-01", "166.60", 590000);
        mes(hglg, "2026-08-01", "165.951408188109", 608345);
        preco("HGLG11", "2025-12-30", "160.00");
        for (int m = 1; m <= 12; m++) {
            provento("HGLG11", "RENDIMENTO", LocalDate.of(2025, m, 28), "1.10");
        }
    }

    @AfterEach
    void restaura() {
        jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = 'PETR4'", petr4PrecoAntes);
        jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = 'HGLG11'", hglgPrecoAntes);
        jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = 'APXR11'", apxrPrecoAntes);
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN "
                + "(SELECT id FROM assets WHERE ticker IN ('PETR4', 'HGLG11', 'APXR11'))");
    }

    private long issuer(String cnpj) {
        return jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, cnpj);
    }

    private void documento(long issuerId, String data, String lucro12m, String pl, String dividendos12m) {
        Long doc = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated)
                VALUES (?, 'DFP', CAST(? AS DATE), 1, true) RETURNING id""", Long.class, issuerId, data);
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, source_doc_id, net_income_parent_ttm, equity_parent,
                                              shares_outstanding, dividends_paid_ttm)
                VALUES (?, CAST(? AS DATE), ?, ?, ?, 12888732761, ?)""",
                issuerId, data, doc, new BigDecimal(lucro12m), new BigDecimal(pl), new BigDecimal(dividendos12m));
    }

    private void mes(long issuerId, String mes, String vpCota, int cotistas) {
        jdbc.update("""
                INSERT INTO fii_monthly (issuer_id, reference_month, version, nav_per_share, shareholders,
                                         real_estate_total, real_estate_companies, receivables_total, total_invested,
                                         liabilities_total, total_assets)
                VALUES (?, CAST(? AS DATE), 1, ?, ?, 7057544916.78, 1051691815.4, 31703049.55, 8289415918.47,
                        1282893011.09, 8850561499.23)""",
                issuerId, mes, new BigDecimal(vpCota), cotistas);
    }

    private void preco(String ticker, String data, String fechamento) {
        jdbc.update("""
                INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
                SELECT id, CAST(? AS DATE), ?, 'B3_COTAHIST' FROM assets WHERE ticker = ?""",
                data, new BigDecimal(fechamento), ticker);
    }

    private void provento(String ticker, String tipo, LocalDate dataCom, String valor) {
        jdbc.update("""
                INSERT INTO asset_cash_events (asset_id, type, label, data_com, value_per_share, source)
                SELECT id, ?, ?, ?, ?, 'B3' FROM assets WHERE ticker = ?""",
                tipo, tipo, java.sql.Date.valueOf(dataCom), new BigDecimal(valor), ticker);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> atual(String ticker) {
        ResponseEntity<Map> r = get(user, "/api/v1/assets/" + ticker + "/indicators", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private BigDecimal indicador(Map<String, Object> atual, String nome) {
        Map<String, Object> item = ((List<Map<String, Object>>) atual.get("indicators")).stream()
                .filter(i -> nome.equals(i.get("indicator"))).findFirst().orElseThrow();
        return item.get("value") == null ? null : numero(item.get("value"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> historico(String ticker, String indicador) {
        ResponseEntity<Map> r = get(user, "/api/v1/assets/" + ticker + "/indicators/" + indicador + "/history",
                Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pontos(Map<String, Object> historico) {
        return (List<Map<String, Object>>) historico.get("points");
    }

    @Test
    @DisplayName("PETR4 atual: DFP 2025 com o preco atual — LPA, VPA, P/L, ROE e payout; DY dos ultimos 12 meses")
    void petr4Atual() {
        Map<String, Object> a = atual("PETR4");

        assertThat(a.get("kind")).isEqualTo("COMPANY");
        assertThat(a.get("basePeriodEnd")).isEqualTo("2025-12-31");
        assertThat(indicador(a, "LPA")).isEqualByComparingTo("8.5446");
        assertThat(indicador(a, "VPA")).isEqualByComparingTo("32.2596");
        assertThat(indicador(a, "PL")).isEqualByComparingTo("5.62");      // 47,99 / 8,5446
        assertThat(indicador(a, "PVP")).isEqualByComparingTo("1.49");     // 47,99 / 32,2598
        assertThat(indicador(a, "ROE")).isEqualByComparingTo("28.17");
        assertThat(indicador(a, "PAYOUT")).isEqualByComparingTo("37.44");
        // 12 meses ate hoje: o dividendo de 2,00 (os de 2025 podem ter saido da janela)
        assertThat(indicador(a, "DY")).isPositive();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("PETR4 historico do DY: 2024 sem provento = 0 (nao nulo); 2025 = 3,21 / 30,00; media com o Atual")
    void petr4HistoricoDy() {
        Map<String, Object> h = historico("PETR4", "dy");

        assertThat(pontos(h)).extracting(p -> p.get("label")).containsExactly("2024", "2025");
        assertThat(numero(pontos(h).get(0).get("value"))).isEqualByComparingTo("0");
        assertThat(numero(pontos(h).get(1).get("value"))).isEqualByComparingTo("10.70");
        BigDecimal atual = numero(((Map<String, Object>) h.get("current")).get("value"));
        BigDecimal media = new BigDecimal("10.70").add(atual).divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
        assertThat(numero(h.get("companyAverage"))).isEqualByComparingTo(media);
    }

    @Test
    @DisplayName("PETR4 historico do LPA e do ROE: 2024 sem PL de inicio fica nulo com motivo")
    void petr4HistoricoLpaRoe() {
        Map<String, Object> lpa = historico("PETR4", "LPA");
        assertThat(pontos(lpa)).extracting(p -> numero(p.get("value")))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2.8402"), new BigDecimal("8.5446"));

        Map<String, Object> roe = historico("PETR4", "roe");
        assertThat(pontos(roe).get(0).get("value")).isNull();
        assertThat(pontos(roe).get(0).get("note")).isEqualTo("sem patrimonio do inicio e do fim do periodo");
        assertThat(numero(pontos(roe).get(1).get("value"))).isEqualByComparingTo("28.17");
    }

    @Test
    @DisplayName("PETR4F usa o preco e os proventos do PETR4 (D8)")
    void fracionario() {
        assertThat(indicador(atual("PETR4F"), "PL")).isEqualByComparingTo("5.62");
    }

    @Test
    @DisplayName("HGLG11: indicadores do informe de ago/2026; DY 2025 = 13,20 / 160,00")
    void hglg() {
        Map<String, Object> a = atual("HGLG11");
        assertThat(a.get("kind")).isEqualTo("FII");
        assertThat(a.get("basePeriodEnd")).isEqualTo("2026-08-31");
        assertThat(indicador(a, "PVP")).isEqualByComparingTo("0.89");
        assertThat(indicador(a, "VP_COTA")).isEqualByComparingTo("165.9514");
        assertThat(indicador(a, "COTISTAS")).isEqualByComparingTo("608345");
        assertThat(indicador(a, "IMOVEIS")).isEqualByComparingTo("97.83");
        assertThat(indicador(a, "ALAVANCAGEM")).isEqualByComparingTo("14.50");

        Map<String, Object> dy = historico("HGLG11", "DY");
        assertThat(pontos(dy)).singleElement().satisfies(p -> {
            assertThat(p.get("label")).isEqualTo("2025");
            assertThat(numero(p.get("value"))).isEqualByComparingTo("8.25");
        });
    }

    @Test
    @DisplayName("FII que ainda nao distribuiu (APXR11): DY 0; sem informe, o resto nulo com motivo")
    void fundoSemDistribuicaoESemInforme() {
        Map<String, Object> a = atual("APXR11");
        assertThat(indicador(a, "DY")).isEqualByComparingTo("0");
        assertThat(indicador(a, "PVP")).isNull();
    }

    @Test
    @DisplayName("Erros: ativo sem emissor 404, indicador invalido 400, indicador de acao em FII 400")
    void erros() {
        assertThat(get(user, "/api/v1/assets/BOVA11/indicators", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(user, "/api/v1/assets/PETR4/indicators/XPTO/history", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(user, "/api/v1/assets/HGLG11/indicators/LPA/history", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Emissor: cadastro com tickers, serie trimestral da companhia, informe do FII")
    @SuppressWarnings("unchecked")
    void emissor() {
        long petr = issuer(PETR);
        Map<String, Object> e = get(user, "/api/v1/issuers/" + petr, Map.class).getBody();
        assertThat(e.get("cnpj")).isEqualTo(PETR);
        assertThat((List<Map<String, Object>>) e.get("tickers")).extracting(t -> t.get("ticker")).contains("PETR4", "PETR3");

        List<Map<String, Object>> q = get(user, "/api/v1/issuers/" + petr + "/quarters?from=2025-01-01", List.class).getBody();
        assertThat(q).singleElement().satisfies(r -> assertThat(r.get("periodEnd")).isEqualTo("2025-12-31"));

        long hglg = issuer(HGLG);
        assertThat(get(user, "/api/v1/issuers/" + hglg + "/months", List.class).getBody()).hasSize(2);
        assertThat(get(user, "/api/v1/issuers/" + hglg + "/quarters", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Quantidade do ultimo balanco em revisao: vale a do anterior, com motivo; com evento no meio, nulo")
    @SuppressWarnings("unchecked")
    void quantidadeEmRevisao() {
        long petr = issuer(PETR);
        Long doc = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated)
                VALUES (?, 'ITR', DATE '2026-06-30', 1, true) RETURNING id""", Long.class, petr);
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, source_doc_id, net_income_parent_ttm, equity_parent)
                VALUES (?, DATE '2026-06-30', ?, 120000000000, 430000000000)""", petr, doc);

        Map<String, Object> atual = atual("PETR4");
        Map<String, Object> lpa = ((List<Map<String, Object>>) atual.get("indicators")).stream()
                .filter(i -> "LPA".equals(i.get("indicator"))).findFirst().orElseThrow();
        // 120 bi / 12.888.732.761 (acoes do DFP 2025)
        assertThat(numero(lpa.get("value"))).isEqualByComparingTo("9.3105");
        assertThat((String) lpa.get("note")).contains("31/12/2025");
        assertThat(indicador(atual, "PL")).isNotNull();

        // desdobramento entre os dois balancos: a quantidade anterior nao vale mais
        jdbc.update("""
                INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source)
                SELECT id, 'DESDOBRAMENTO', DATE '2026-03-02', 100, 'B3' FROM assets WHERE ticker = 'PETR4'""");
        assertThat(indicador(atual("PETR4"), "LPA")).isNull();
    }
}
