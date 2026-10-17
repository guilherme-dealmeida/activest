package com.primevest.functional;

import com.primevest.domain.calendar.BusinessCalendar;
import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rentabilidade pela API (docs/planos/PLANO_RENTABILIDADE.md, secao 8): linha do tempo a partir das transacoes e do
 * historico de preco, TWR, cobertura (C6), o ultimo ponto igual a Carteira (C16) e o escopo por dono.
 */
class RentabilidadeIT extends FuncionalBase {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);

    private User dono;
    private User outro;

    @BeforeEach
    void usuarios() {
        dono = usuarioComCarteira("rent-dono@primevest.test");
        outro = usuarioComCarteira("rent-outro@primevest.test");
    }

    /** Fechamento de cada pregao de {@code INICIO} ate hoje: 30,00 no primeiro dia, 33,00 dali em diante. */
    private void cotacoes(String ticker) {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Long id = jdbc.queryForObject("SELECT id FROM assets WHERE ticker = ?", Long.class, ticker);
        for (LocalDate d = INICIO; !d.isAfter(hoje); d = d.plusDays(1)) {
            if (BusinessCalendar.isBusinessDay(d)) {
                jdbc.update("INSERT INTO asset_price_history (asset_id, reference_date, close_price, source) VALUES (?, ?, ?, 'BRAPI') "
                        + "ON CONFLICT (asset_id, reference_date) DO UPDATE SET close_price = EXCLUDED.close_price",
                        id, d, d.equals(INICIO) ? 30.00 : 33.00);
            }
        }
        jdbc.update("UPDATE assets SET current_price = 33.00 WHERE id = ?", id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resumo(User user, String query) {
        ResponseEntity<Map> r = get(user, "/api/v1/performance/summary" + query, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @Test
    @DisplayName("C1/C16: 10 cotas a 30 que fecham a 33 = 10%; o ultimo ponto e o valor da Carteira")
    @SuppressWarnings("unchecked")
    void twrEUltimoPonto() {
        cotacoes(CatalogIds.ACAO);
        lancarComSucesso(dono, compra(CatalogIds.ACAO, INICIO.toString(), "10", "30.00"));

        Map<String, Object> r = resumo(dono, "?basis=GROSS");

        assertThat(numero(r.get("twr"))).isEqualByComparingTo("10");
        assertThat(numero(r.get("valueEnd"))).isEqualByComparingTo(numero(posicaoDe(dono, CatalogIds.ACAO).get("currentValue")));
        Map<String, Object> cov = (Map<String, Object>) r.get("coverage");
        assertThat(cov.get("available")).isEqualTo(true);
        assertThat(cov.get("firstDate")).isEqualTo(INICIO.toString());
    }

    @Test
    @DisplayName("C2: um aporte no meio, ao preco do dia anterior, nao muda o TWR")
    void aporteNaoERendimento() {
        cotacoes(CatalogIds.ACAO);
        lancarComSucesso(dono, compra(CatalogIds.ACAO, INICIO.toString(), "10", "30.00"));
        LocalDate dia = INICIO.plusDays(10);
        while (!BusinessCalendar.isBusinessDay(dia)) {
            dia = dia.plusDays(1);
        }
        lancarComSucesso(dono, compra(CatalogIds.ACAO, dia.toString(), "100", "33.00"));

        assertThat(numero(resumo(dono, "?basis=GROSS").get("twr"))).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("C6: ativo sem preco deixa o periodo indisponivel, com o culpado; filtrando a classe, calcula")
    @SuppressWarnings("unchecked")
    void semPrecoIndisponivel() {
        cotacoes(CatalogIds.ACAO);
        lancarComSucesso(dono, compra(CatalogIds.ACAO, INICIO.toString(), "10", "30.00"));
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id = (SELECT id FROM assets WHERE ticker = ?)",
                CatalogIds.FII);
        lancarComSucesso(dono, compra(CatalogIds.FII, INICIO.toString(), "1", "100.00")); // FII sem historico

        Map<String, Object> r = resumo(dono, "");
        Map<String, Object> cov = (Map<String, Object>) r.get("coverage");
        assertThat(cov.get("available")).isEqualTo(false);
        assertThat((List<String>) cov.get("unavailable")).contains(CatalogIds.FII);
        assertThat(r.get("twr")).isNull();

        assertThat(numero(resumo(dono, "?type=ACAO").get("twr"))).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Escopo por dono: posicao de outro usuario responde 404; parametro invalido 422")
    @SuppressWarnings("unchecked")
    void escopo() {
        cotacoes(CatalogIds.ACAO);
        lancarComSucesso(dono, compra(CatalogIds.ACAO, INICIO.toString(), "10", "30.00"));
        Object posicao = posicaoDe(dono, CatalogIds.ACAO).get("id");

        assertThat(get(outro, "/api/v1/performance/summary?position=" + posicao, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(dono, "/api/v1/performance/summary?range=2X", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> vazio = resumo(outro, "");
        assertThat(((Map<String, Object>) vazio.get("coverage")).get("empty")).isEqualTo(true);
    }

    @Test
    @DisplayName("Catalogo de benchmarks: os 8 da V57, CDI pre-selecionado, padrao por classe (D4)")
    @SuppressWarnings("unchecked")
    void catalogo() {
        Map<String, Object> c = get(dono, "/api/v1/performance/benchmarks", Map.class).getBody();
        List<Map<String, Object>> list = (List<Map<String, Object>>) c.get("benchmarks");
        assertThat(list).extracting(m -> m.get("code"))
                .contains("CDI", "SELIC", "IPCA", "IPCA_MAIS", "IBOV", "IFIX", "SP500_IVVB11", "BTC");
        assertThat(list.stream().filter(m -> Boolean.TRUE.equals(m.get("defaultSelected"))).map(m -> m.get("code")))
                .containsExactly("CDI");
        Map<String, Object> padrao = (Map<String, Object>) c.get("defaultsByType");
        assertThat(padrao.get("ACAO")).isEqualTo(List.of("IBOV"));
        // Tesouro sem taxa gravada: so o IPCA
        assertThat(padrao.get("TESOURO_DIRETO")).isEqualTo(List.of("IPCA"));
        assertThat(c.get("treasuryIpca")).isNull();
    }

    @Test
    @DisplayName("Tesouro: IPCA e IPCA + a taxa do Tesouro IPCA+ a venda mais curto (o 2029 fora da venda nao conta)")
    @SuppressWarnings("unchecked")
    void padraoDoTesouro() {
        String sql = "INSERT INTO treasury_offers (asset_id, rate_date, buy_rate, sell_rate, on_sale, sale_date) "
                + "SELECT id, DATE '2026-10-15', ?, ?, ?, DATE '2026-10-14' FROM assets WHERE ticker = ?";
        jdbc.update(sql, 6.77, 6.89, false, "Tesouro IPCA+ 2029");
        jdbc.update(sql, 6.83, 6.95, true, "Tesouro IPCA+ 2032");
        jdbc.update(sql, 6.73, 6.85, true, "Tesouro IPCA+ 2040");
        jdbc.update(sql, 6.79, 6.91, true, "Tesouro IPCA+ com Juros Semestrais 2030");
        try {
            Map<String, Object> c = get(dono, "/api/v1/performance/benchmarks", Map.class).getBody();
            assertThat(((Map<String, Object>) c.get("defaultsByType")).get("TESOURO_DIRETO"))
                    .isEqualTo(List.of("IPCA", "IPCA_MAIS:6.83"));
            Map<String, Object> ref = (Map<String, Object>) c.get("treasuryIpca");
            assertThat(ref.get("ticker")).isEqualTo("Tesouro IPCA+ 2032");
            assertThat(ref.get("rateDate")).isEqualTo("2026-10-15");
        } finally {
            jdbc.update("DELETE FROM treasury_offers");
        }
    }
}
