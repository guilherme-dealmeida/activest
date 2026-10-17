package com.primevest.functional;

import com.primevest.domain.calendar.BusinessCalendar;
import com.primevest.domain.notification.DailyOpeningService;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.AfterEach;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Rotina de abertura do dia [B7] e as mensagens que ela produz (docs/planos/PLANO_NOTIFICACOES.md, N6–N8): provento
 * pago em D0, evento com data-ex hoje, bonificação sem custo com "Informar custo" e o aviso do título vencido
 * (docs/planos/PLANO_VENDA_RENDA_FIXA.md, seção 6: a liquidação automática saiu).
 */
class AberturaDoDiaIT extends FuncionalBase {

    @Autowired
    DailyOpeningService abertura;

    @Autowired
    com.primevest.domain.notification.NotificationService notificacoes;

    private User dono;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("abertura@primevest.test");
    }

    @AfterEach
    void limparTesouro() {
        jdbc.update("DELETE FROM asset_price_history WHERE source = 'TESOURO'");
    }

    private List<Map<String, Object>> mensagens(String kind) {
        return jdbc.queryForList("SELECT kind, status, title, body, payload::text AS payload FROM notifications "
            + "WHERE kind = ? ORDER BY created_at", kind);
    }

    private Object posicaoId(String ticker) {
        return jdbc.queryForObject("SELECT pa.id FROM portfolio_assets pa JOIN assets a ON a.id = pa.asset_id "
            + "WHERE a.ticker = ? LIMIT 1", Object.class, ticker);
    }

    private void evento(String tipo, String dataCom, String fator) {
        jdbc.update("""
            INSERT INTO asset_stock_events (asset_id, type, data_com, factor, source)
            SELECT id, ?, CAST(? AS DATE), CAST(? AS NUMERIC), 'B3' FROM assets WHERE ticker = ?""",
            tipo, dataCom, fator, CatalogIds.ACAO);
    }

    private void cdi(LocalDate de, LocalDate ate) {
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            if (BusinessCalendar.isBusinessDay(d)) {
                jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) "
                    + "SELECT id, ?, 0.05 FROM market_indices WHERE code = 'CDI' ON CONFLICT DO NOTHING", Date.valueOf(d));
            }
        }
    }

    private Map<String, Object> cdb(String data, String valor, String vencimento) {
        Map<String, Object> c = aplicacaoEmCdb(CatalogIds.CDB, data, valor, "BANCO TESTE");
        c.put("indexerRate", new BigDecimal("100"));
        c.put("maturityDate", vencimento);
        return c;
    }

    // ------------------------------------------------------------------ proventos D0

    @Test
    @DisplayName("Provento pago hoje: uma mensagem por ativo com uma linha por provento; outro dia ou de novo, nada")
    void proventoPagoHoje() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-01-05", "100", "30.00"));
        Object pa = posicaoId(CatalogIds.ACAO);
        LocalDate hoje = LocalDate.of(2026, 10, 13);
        jdbc.update("INSERT INTO dividends (portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, "
            + "type, shares_on_date) VALUES (?, 0.5, 50.00, DATE '2026-09-01', ?, 'DIVIDENDO', 100)", pa, Date.valueOf(hoje));
        jdbc.update("INSERT INTO dividends (portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, "
            + "type, shares_on_date) VALUES (?, 0.2, 20.00, DATE '2026-09-01', ?, 'JCP', 100)", pa, Date.valueOf(hoje));
        jdbc.update("INSERT INTO dividends (portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, "
            + "type, shares_on_date) VALUES (?, 0.9, 90.00, DATE '2026-09-01', DATE '2026-12-01', 'DIVIDENDO', 100)", pa);

        abertura.run(hoje, "teste");
        abertura.run(hoje, "teste de novo");

        List<Map<String, Object>> m = mensagens("PROVENTO_PAGO");
        assertThat(m).hasSize(1);
        assertThat((String) m.get(0).get("title")).startsWith("PETR4 — R$").contains("70,00");
        assertThat((String) m.get(0).get("payload")).contains("Dividendo").contains("JCP");
        assertThat(m.get(0).get("status")).isNull();

        abertura.run(LocalDate.of(2026, 10, 14), "dia sem pagamento");
        assertThat(mensagens("PROVENTO_PAGO")).hasSize(1);
    }

    // ------------------------------------------------------------------ eventos D0

    @Test
    @DisplayName("Desdobramento com data-ex hoje: mensagem com as cotas antes e depois; sem posição na data-com, nada")
    void eventoAplicadoHoje() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-01-05", "100", "30.00"));
        evento("DESDOBRAMENTO", "2026-10-08", "100"); // quinta; data-ex sexta 09/10

        abertura.run(LocalDate.of(2026, 10, 8), "véspera");
        assertThat(mensagens("EVENTO_APLICADO")).isEmpty();

        abertura.run(LocalDate.of(2026, 10, 9), "data-ex");
        abertura.run(LocalDate.of(2026, 10, 9), "de novo");
        List<Map<String, Object>> m = mensagens("EVENTO_APLICADO");
        assertThat(m).hasSize(1);
        assertThat((String) m.get(0).get("title")).isEqualTo("PETR4 — desdobramento de 1 para 2 aplicado");
        assertThat((String) m.get(0).get("body")).contains("de 100 para 200");
    }

    // ------------------------------------------------------------------ bonificação sem custo

    @Test
    @DisplayName("Bonificação sem custo: pendência com Informar custo e ícone na Carteira; informar resolve e recalcula o preço médio")
    @SuppressWarnings("unchecked")
    void bonificacaoSemCusto() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2025-01-06", "100", "30.00"));
        evento("BONIFICACAO", "2025-06-02", "10");

        abertura.run(LocalDate.of(2026, 10, 13), "teste");
        List<Map<String, Object>> m = mensagens("BONIFICACAO_SEM_CUSTO");
        assertThat(m).hasSize(1);
        assertThat(m.get(0).get("status")).isEqualTo("NAO_RESOLVIDA");
        assertThat((String) m.get(0).get("payload")).contains("INFORMAR_CUSTO").contains("Ações novas na sua posição: 10");

        Map<String, Object> pos = posicaoDe(dono, CatalogIds.ACAO);
        List<Map<String, Object>> pend = (List<Map<String, Object>>) pos.get("pendingBonusCosts");
        assertThat(pend).hasSize(1);
        assertThat(numero(pos.get("averagePrice"))).isEqualByComparingTo(new BigDecimal("3000").divide(new BigDecimal("110"), 8, java.math.RoundingMode.HALF_UP));

        Object eventId = pend.get(0).get("stockEventId");
        ResponseEntity<String> r = put(dono, "/api/v1/portfolio/assets/" + pos.get("id") + "/stock-events/" + eventId
            + "/cost", Map.of("costPerShare", new BigDecimal("12.50")), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> depois = posicaoDe(dono, CatalogIds.ACAO);
        assertThat((List<?>) depois.get("pendingBonusCosts")).isEmpty();
        // (3000 + 10 × 12,50) / 110
        assertThat(numero(depois.get("averagePrice")).doubleValue()).isCloseTo(3125.0 / 110, within(0.0001));
        assertThat(mensagens("BONIFICACAO_SEM_CUSTO").get(0).get("status")).isEqualTo("RESOLVIDA");

        abertura.run(LocalDate.of(2026, 10, 14), "dia seguinte");
        assertThat(mensagens("BONIFICACAO_SEM_CUSTO")).hasSize(1);
    }

    @Test
    @DisplayName("Informar custo de posição de outro usuário: 404")
    void informarCustoDeOutro() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2025-01-06", "100", "30.00"));
        evento("BONIFICACAO", "2025-06-02", "10");
        User outro = usuarioComCarteira("outro-abertura@primevest.test");
        Object eventId = jdbc.queryForObject("SELECT id FROM asset_stock_events LIMIT 1", Object.class);

        ResponseEntity<String> r = put(outro, "/api/v1/portfolio/assets/" + posicaoId(CatalogIds.ACAO)
            + "/stock-events/" + eventId + "/cost", Map.of("costPerShare", new BigDecimal("12.50")), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ título vencido (PLANO_VENDA_RENDA_FIXA, 6)

    private Map<String, Object> liquidacao(Object noteId, String data, BigDecimal liquido) {
        Map<String, Object> v = new HashMap<>();
        v.put("ticker", CatalogIds.CDB);
        v.put("type", "SELL");
        v.put("transactionDate", data);
        v.put("totalAmount", liquido);
        v.put("isFullSettlement", true);
        v.put("noteId", noteId);
        return v;
    }

    @Test
    @DisplayName("Compra retroativa de CDB já vencido: nenhuma venda; pendência RF_VENCIDO com o líquido estimado e o link preenchido")
    void retroativaVencidaAvisa() {
        cdi(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));
        lancarComSucesso(dono, cdb("2024-01-02", "1000.00", "2024-01-15"));

        assertThat(transacoesDe(dono)).noneMatch(t -> t.type().name().equals("SELL"));
        assertThat(carteiraDe(dono)).anyMatch(p -> CatalogIds.CDB.equals(p.get("ticker")));
        List<Map<String, Object>> m = mensagens("RF_VENCIDO");
        assertThat(m).hasSize(1);
        assertThat(m.get(0).get("status")).isEqualTo("NAO_RESOLVIDA");
        assertThat((String) m.get(0).get("title")).isEqualTo("CDB BANCO TESTE — vencido em 15/01/2024, lance a liquidação");
        assertThat((String) m.get(0).get("body")).contains("líquido estimado");
        assertThat((String) m.get(0).get("payload")).contains("/transactions?settle=").contains("date=2024-01-15")
            .contains("amount=");
        assertThat(mensagens("RF_LIQUIDADA")).isEmpty();
    }

    @Test
    @DisplayName("Liquidação lançada pelo líquido: bruto pela curva, IR e IOF calculados; a pendência se resolve")
    void liquidacaoPeloLiquidoResolve() {
        cdi(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));
        lancarComSucesso(dono, cdb("2024-01-02", "1000.00", "2024-01-15"));
        Object noteId = transacoesDe(dono).get(0).noteId();

        // 13 dias: IOF 56% do rendimento; IR 22,5% sobre o rendimento menos o IOF (truncados)
        double bruto = Math.floor(1000 * Math.pow(1.0005, 9) * 100 + 0.5) / 100;
        double rendimento = bruto - 1000;
        double iof = Math.floor(rendimento * 0.56 * 100) / 100;
        double ir = Math.floor((rendimento - iof) * 0.225 * 100) / 100;
        BigDecimal liquido = BigDecimal.valueOf(bruto - iof - ir).setScale(2, java.math.RoundingMode.HALF_UP);

        lancarComSucesso(dono, liquidacao(noteId, "2024-01-15", liquido));

        TransactionResponseDTO venda = transacoesDe(dono).stream().filter(t -> t.type().name().equals("SELL"))
            .findFirst().orElseThrow();
        assertThat(venda.origin().name()).isEqualTo("MANUAL");
        assertThat(venda.netAmount()).isEqualByComparingTo(liquido);
        assertThat(venda.totalAmount().doubleValue()).isCloseTo(bruto, within(0.02));
        assertThat(venda.iofTax().doubleValue()).isCloseTo(iof, within(0.02));
        assertThat(venda.incomeTax().doubleValue()).isCloseTo(ir, within(0.02));
        assertThat(carteiraDe(dono)).noneMatch(p -> CatalogIds.CDB.equals(p.get("ticker")));
        assertThat(mensagens("RF_VENCIDO").get(0).get("status")).isEqualTo("RESOLVIDA");
    }

    @Test
    @DisplayName("Abertura avisa o CDB que venceu, uma vez só, e não lança venda; resolve as pendências antigas (D7)")
    void aberturaAvisa() {
        cdi(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));
        lancarComSucesso(dono, cdb("2024-01-02", "1000.00", "2030-01-15"));
        // o título "venceu": o vencimento passa para 15/01/2024
        jdbc.update("UPDATE fixed_income_notes SET maturity_date = DATE '2024-01-15'");
        notificacoes.notify(com.primevest.domain.notification.NotificationDraft
            .of(com.primevest.domain.notification.NotificationKind.RF_LIQUIDACAO_DIVERGENTE).user(dono.getId())
            .title("antiga").body("antiga").key("antiga").build());

        abertura.run(LocalDate.of(2026, 10, 13), "teste");
        abertura.run(LocalDate.of(2026, 10, 14), "dia seguinte");

        assertThat(mensagens("RF_VENCIDO")).hasSize(1);
        assertThat(transacoesDe(dono)).noneMatch(t -> t.type().name().equals("SELL"));
        assertThat(mensagens("RF_LIQUIDACAO_DIVERGENTE").get(0).get("status")).isEqualTo("RESOLVIDA");
    }

    @Test
    @DisplayName("CDB vencido sem curva (TR): sem transação, pendência RF_VENCIDO para informar o valor recebido")
    void semCurvaViraPendencia() {
        Map<String, Object> c = cdb("2024-01-02", "1000.00", "2024-01-15");
        c.put("rateType", "HIBRIDO");
        c.put("indexer", "TR");
        c.put("indexerRate", new BigDecimal("2"));
        lancarComSucesso(dono, c);

        assertThat(transacoesDe(dono)).noneMatch(t -> t.type().name().equals("SELL"));
        List<Map<String, Object>> m = mensagens("RF_VENCIDO");
        assertThat(m).hasSize(1);
        assertThat(m.get(0).get("status")).isEqualTo("NAO_RESOLVIDA");
        assertThat((String) m.get(0).get("body")).contains("não tem como calcular");
        assertThat(mensagens("RF_LIQUIDACAO_SEM_VALOR")).isEmpty();
    }
}
