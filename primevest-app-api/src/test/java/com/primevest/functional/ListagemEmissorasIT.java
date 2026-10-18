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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listagem de emissoras (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 4 e 6): uma aba inteira por chamada, com os
 * mesmos valores de {@code GET /assets/{ticker}/indicators}, sem fracionarios, na ordem do PL decrescente e com as
 * marcas de dado antigo.
 */
class ListagemEmissorasIT extends FuncionalBase {

    private static final List<String> TICKERS = List.of("PETR3", "PETR4", "VALE3", "HGLG11", "XPML11");

    private final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private final Map<String, BigDecimal> precosAntes = new HashMap<>();
    private User user;

    @BeforeEach
    void cenario() {
        user = usuarioComCarteira("listagem@primevest.test");
        for (String t : TICKERS) {
            precosAntes.put(t, jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = ?",
                    BigDecimal.class, t));
        }
        jdbc.update("UPDATE assets SET current_price = NULL WHERE ticker IN ('PETR3', 'PETR4', 'VALE3', 'HGLG11', 'XPML11')");
        limparPrecos();

        // PETR4 sem pregao ha 10 dias (marca); PETR3 com pregao de 2 dias atras
        preco("PETR4", hoje.minusDays(10), "47.99");
        preco("PETR3", hoje.minusDays(2), "51.20");
        preco("VALE3", hoje.minusDays(1), "60.00");
        preco("HGLG11", hoje.minusDays(1), "150.00");
        preco("XPML11", hoje.minusDays(1), "100.00");
        provento("PETR4", "DIVIDENDO", hoje.minusDays(30), "2.00");
        provento("HGLG11", "RENDIMENTO", hoje.minusDays(20), "1.10");

        // Petrobras: balanco de 31/12/2025 (mais de 6 meses: marca); Vale: o do ultimo trimestre fechado, com PL
        // negativo de proposito (vem depois dos positivos e antes dos vazios)
        balanco(emissor("PETR4"), LocalDate.of(2025, 12, 31), "110129000000", "415786000000", 12888732761L);
        balanco(emissor("VALE3"), fimDoTrimestreAnterior(), "-5000000000", "-1000000000", 4268000000L);

        // HGLG11 com informe de 2 meses atras; XPML11 com informe de 5 meses atras (marca)
        informe(emissor("HGLG11"), hoje.minusMonths(2).withDayOfMonth(1), "165.95", "7567668488.14");
        informe(emissor("XPML11"), hoje.minusMonths(5).withDayOfMonth(1), "110.00", "7081112753.36");
    }

    @AfterEach
    void restaura() {
        limparPrecos();
        precosAntes.forEach((t, p) -> jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = ?", p, t));
    }

    @Test
    @DisplayName("Aba Ações: sem fracionários, PL decrescente com desempate pelo ticker, vazios no fim")
    void abaAcoes() {
        List<Map<String, Object>> linhas = listagem("COMPANY");
        List<String> tickers = linhas.stream().map(l -> (String) l.get("ticker")).toList();

        assertThat(tickers).contains("PETR3", "PETR4", "VALE3").doesNotContain("PETR3F", "PETR4F", "VALE3F");
        assertThat(tickers.subList(0, 3)).containsExactly("PETR3", "PETR4", "VALE3");
        assertThat(linhas.subList(3, linhas.size())).allMatch(l -> l.get("equity") == null);
        assertThat(tickers.subList(3, tickers.size())).isSorted();
        assertThat(linhas).noneMatch(l -> "HGLG11".equals(l.get("ticker")));

        Map<String, Object> petr4 = linhas.get(1);
        Map<String, Object> cadastro = jdbc.queryForMap("""
                SELECT a.name, i.trade_name, i.legal_name, i.b3_sector, i.b3_subsector, i.b3_segment
                FROM assets a JOIN issuers i ON i.id = a.issuer_id WHERE a.ticker = 'PETR4'""");
        Object pregao = cadastro.get("trade_name");
        // F1: nome de pregao; sem ele, o nome do ativo
        assertThat(petr4.get("name")).isEqualTo(pregao == null || pregao.toString().isBlank()
                ? cadastro.get("name") : pregao);
        assertThat(petr4.get("legalName")).isEqualTo(cadastro.get("legal_name"));
        assertThat(petr4.get("sector")).isEqualTo(cadastro.get("b3_sector"));
        assertThat(petr4.get("subsector")).isEqualTo(cadastro.get("b3_subsector"));
        assertThat(petr4.get("segment")).isEqualTo(cadastro.get("b3_segment"));
        assertThat(numero(petr4.get("equity"))).isEqualByComparingTo("415786000000");
    }

    @Test
    @DisplayName("Os valores da lista são os da página da emissora")
    void mesmosValoresDaEmissora() {
        Map<String, Map<String, Object>> acoes = porTicker(listagem("COMPANY"));
        Map<String, Map<String, Object>> fiis = porTicker(listagem("FII"));
        for (String t : TICKERS) {
            Map<String, Object> linha = t.endsWith("11") ? fiis.get(t) : acoes.get(t);
            Map<String, Object> pagina = atual(t);
            assertThat(linha.get("indicators")).as(t).isEqualTo(pagina.get("indicators"));
            assertThat(linha.get("price")).as(t).isEqualTo(pagina.get("price"));
            assertThat(linha.get("basePeriodEnd")).as(t).isEqualTo(pagina.get("basePeriodEnd"));
        }
    }

    @Test
    @DisplayName("Marca de dado antigo: preço há mais de 7 dias, balanço há mais de 6 meses, informe há mais de 3")
    void dadoAntigo() {
        Map<String, Map<String, Object>> acoes = porTicker(listagem("COMPANY"));
        assertThat(acoes.get("PETR4").get("priceStale")).isEqualTo(true);
        assertThat(acoes.get("PETR4").get("priceDate")).isEqualTo(hoje.minusDays(10).toString());
        assertThat(acoes.get("PETR3").get("priceStale")).isEqualTo(false);
        assertThat(acoes.get("PETR4").get("baseStale")).isEqualTo(true);
        assertThat(acoes.get("VALE3").get("baseStale")).isEqualTo(false);

        Map<String, Map<String, Object>> fiis = porTicker(listagem("FII"));
        assertThat(fiis.get("HGLG11").get("baseStale")).isEqualTo(false);
        assertThat(fiis.get("XPML11").get("baseStale")).isEqualTo(true);
    }

    @Test
    @DisplayName("Aba FIIs: PL do informe, segmento do fundo e nome do ativo")
    void abaFiis() {
        List<Map<String, Object>> linhas = listagem("FII");
        assertThat(linhas.subList(0, 2)).extracting(l -> l.get("ticker")).containsExactly("HGLG11", "XPML11");
        assertThat(linhas).noneMatch(l -> "PETR4".equals(l.get("ticker")));

        Map<String, Object> hglg = linhas.get(0);
        Map<String, Object> cadastro = jdbc.queryForMap("""
                SELECT a.name, i.legal_name, i.segment FROM assets a JOIN issuers i ON i.id = a.issuer_id
                WHERE a.ticker = 'HGLG11'""");
        assertThat(hglg.get("name")).isEqualTo(cadastro.get("name"));
        assertThat(hglg.get("legalName")).isEqualTo(cadastro.get("legal_name"));
        assertThat(hglg.get("segment")).isEqualTo(cadastro.get("segment"));
        assertThat(hglg.get("sector")).isNull();
        assertThat(numero(hglg.get("equity"))).isEqualByComparingTo("7567668488.14");
    }

    @Test
    @DisplayName("Sem login: 401; tipo inválido: 400")
    void acesso() {
        assertThat(rest.getForEntity("/api/v1/assets/indicators?kind=FII", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(user, "/api/v1/assets/indicators?kind=ETF", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ apoio

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listagem(String kind) {
        ResponseEntity<List> r = get(user, "/api/v1/assets/indicators?kind=" + kind, List.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> atual(String ticker) {
        ResponseEntity<Map> r = get(user, "/api/v1/assets/" + ticker + "/indicators", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private static Map<String, Map<String, Object>> porTicker(List<Map<String, Object>> linhas) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        linhas.forEach(l -> out.put((String) l.get("ticker"), l));
        return out;
    }

    private LocalDate fimDoTrimestreAnterior() {
        LocalDate inicioDoTrimestre = hoje.withDayOfMonth(1).minusMonths((hoje.getMonthValue() - 1) % 3);
        return inicioDoTrimestre.minusDays(1);
    }

    private long emissor(String ticker) {
        return jdbc.queryForObject("SELECT issuer_id FROM assets WHERE ticker = ?", Long.class, ticker);
    }

    private void limparPrecos() {
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker IN "
                + "('PETR3', 'PETR4', 'VALE3', 'HGLG11', 'XPML11'))");
    }

    private void preco(String ticker, LocalDate data, String fechamento) {
        jdbc.update("""
                INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
                SELECT id, ?, ?, 'B3_COTAHIST' FROM assets WHERE ticker = ?""",
                java.sql.Date.valueOf(data), new BigDecimal(fechamento), ticker);
    }

    private void provento(String ticker, String tipo, LocalDate dataCom, String valor) {
        jdbc.update("""
                INSERT INTO asset_cash_events (asset_id, type, label, data_com, value_per_share, source)
                SELECT id, ?, ?, ?, ?, 'B3' FROM assets WHERE ticker = ?""",
                tipo, tipo, java.sql.Date.valueOf(dataCom), new BigDecimal(valor), ticker);
    }

    private void balanco(long issuerId, LocalDate fim, String lucro12m, String pl, long acoes) {
        Long doc = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated)
                VALUES (?, 'ITR', ?, 1, true) RETURNING id""", Long.class, issuerId, java.sql.Date.valueOf(fim));
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, source_doc_id, net_income_parent_ttm, equity_parent,
                                              shares_outstanding, dividends_paid_ttm)
                VALUES (?, ?, ?, ?, ?, ?, 0)""",
                issuerId, java.sql.Date.valueOf(fim), doc, new BigDecimal(lucro12m), new BigDecimal(pl), acoes);
    }

    private void informe(long issuerId, LocalDate mes, String vpCota, String pl) {
        jdbc.update("""
                INSERT INTO fii_monthly (issuer_id, reference_month, version, nav_per_share, shareholders,
                                         real_estate_total, real_estate_companies, receivables_total, total_invested,
                                         liabilities_total, total_assets, net_assets)
                VALUES (?, ?, 1, ?, 600000, 7057544916.78, 1051691815.4, 31703049.55, 8289415918.47,
                        1282893011.09, 8850561499.23, ?)""",
                issuerId, java.sql.Date.valueOf(mes), new BigDecimal(vpCota), new BigDecimal(pl));
    }
}
