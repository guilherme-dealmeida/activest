package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Desdobramento, grupamento e bonificacao pela API, do jeito que a tela ve
 * (PLANO_BRAPI.md, F6). O evento e gravado no catalogo; as transacoes do
 * usuario nunca sao reescritas.
 */
class EventosSocietariosIT extends FuncionalBase {

    private User dono;

    @BeforeEach
    void usuario() {
        dono = usuarioComCarteira("eventos@primevest.test");
    }

    private void evento(String ticker, String tipo, String dataCom, String fator) {
        jdbc.update("""
            INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source)
            SELECT id, ?, CAST(? AS DATE), CAST(? AS NUMERIC), 'B3' FROM assets WHERE ticker = ?""",
            tipo, dataCom, fator, ticker);
    }

    @Test
    @DisplayName("Desdobramento 1:2: dobra as cotas, mantem o custo, e a venda na quantidade nova e aceita")
    void desdobramentoUmParaDois_dobraCotasEMantemCusto() {
        evento(CatalogIds.ACAO, "DESDOBRAMENTO", "2026-04-15", "100");
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-03-02", "100", "30.00"));

        // A nota de corretagem depois do evento ja vem na quantidade nova.
        lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-05-04", "150", "16.00"));

        Map<String, Object> posicao = posicaoDe(dono, CatalogIds.ACAO);
        assertThat(numero(posicao.get("totalShares"))).isEqualByComparingTo("50");
        assertThat(numero(posicao.get("averagePrice"))).isEqualByComparingTo("15.0000");
        assertThat(numero(posicao.get("totalInvested"))).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Sem o evento, a mesma venda seria recusada por falta de saldo (regra B)")
    void semEventoVendaRecusada() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-03-02", "100", "30.00"));

        assertThat(lancarEsperandoErro(dono, venda(CatalogIds.ACAO, "2026-05-04", "150", "16.00"))
            .getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("Bonificacao sem custo informado (B27): cotas novas na hora, com custo zero provisorio")
    void bonificacaoSemCusto_entraComCustoZeroProvisorio() {
        evento(CatalogIds.ACAO, "BONIFICACAO", "2026-05-29", "10");
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-03-02", "100", "20.00"));

        Map<String, Object> posicao = posicaoDe(dono, CatalogIds.ACAO);
        assertThat(numero(posicao.get("totalShares"))).isEqualByComparingTo("110");
        assertThat(numero(posicao.get("totalInvested"))).isEqualByComparingTo("2000.00");
        assertThat(numero(posicao.get("averagePrice"))).isEqualByComparingTo("18.1818");
    }

    @Test
    @DisplayName("Grupamento 10:1 com fracao: sobra a parte inteira e a fracao sai do custo")
    void grupamentoComFracao() {
        evento(CatalogIds.ACAO, "GRUPAMENTO", "2026-04-15", "0.1");
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-03-02", "105", "20.00"));

        Map<String, Object> posicao = posicaoDe(dono, CatalogIds.ACAO);
        assertThat(numero(posicao.get("totalShares"))).isEqualByComparingTo("10");
        assertThat(numero(posicao.get("totalInvested"))).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Provento com data-com depois do desdobramento e pago sobre as cotas novas")
    @SuppressWarnings("unchecked")
    void proventoDepoisDoDesdobramento() {
        evento(CatalogIds.ACAO, "DESDOBRAMENTO", "2026-04-15", "100");
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
            SELECT id, 'DIVIDENDO', 'DIVIDENDO', DATE '2026-06-30', DATE '2026-07-20', 0.50, 'B3'
            FROM assets WHERE ticker = ?""", CatalogIds.ACAO);

        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-03-02", "100", "30.00"));

        Map<String, Object> resumo = get(dono, "/api/v1/dividends/", Map.class).getBody();
        List<Map<String, Object>> recentes = (List<Map<String, Object>>) resumo.get("recent");
        assertThat(recentes).singleElement()
            .satisfies(p -> assertThat(numero(p.get("totalAmount"))).isEqualByComparingTo("100.00"));
    }
}
