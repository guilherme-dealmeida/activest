package com.primevest.functional;

import com.primevest.domain.calendar.BusinessCalendar;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.portfolio.PositionRefreshService;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * R4-R6 do docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md pela API: a curva da renda fixa privada (com o CDI gravado no
 * banco, como a adm-api faria), o aviso de indice novo e o Tesouro pelo preco oficial.
 */
class RendaFixaCurvaIT extends FuncionalBase {

    @Autowired
    PositionRefreshService positionRefreshService;

    private User dono;

    /** CDI de 0,05% a.d. em cada dia util de {@code de} a {@code ate}. */
    private void cdi(LocalDate de, LocalDate ate) {
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            if (BusinessCalendar.isBusinessDay(d)) {
                jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) "
                        + "SELECT id, ?, 0.05 FROM market_indices WHERE code = 'CDI' ON CONFLICT DO NOTHING", Date.valueOf(d));
            }
        }
    }

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("curva@primevest.test");
        cdi(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));
    }

    @AfterEach
    void limparTesouro() {
        jdbc.update("DELETE FROM asset_price_history WHERE source = 'TESOURO'");
        jdbc.update("UPDATE assets SET current_price = NULL WHERE asset_type_id = "
                + "(SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')");
    }

    private Map<String, Object> cdb100(String data, String valor) {
        Map<String, Object> c = aplicacaoEmCdb(CatalogIds.CDB, data, valor, "BANCO TESTE");
        c.put("indexerRate", new BigDecimal("100"));
        return c;
    }

    @Test
    @DisplayName("CDB 100% do CDI: a compra ja sai valorizada pela curva ate o ultimo CDI (22 dias uteis de janeiro)")
    void curvaNaCompra() {
        lancarComSucesso(dono, cdb100("2024-01-02", "1000.00"));

        Map<String, Object> p = posicaoDe(dono, CatalogIds.CDB);
        assertThat(numero(p.get("currentValue")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 22), within(0.01));
        assertThat(p).containsEntry("valuationBasis", "CURVA").containsEntry("valuationDate", "2024-01-31")
                .containsEntry("matured", false);
    }

    @Test
    @DisplayName("Resgate parcial rende so o que ficou; excluir o resgate volta para a curva (PENDENCIAS 10.2)")
    void resgateEExclusao() {
        TransactionResponseDTO compra = lancarComSucesso(dono, cdb100("2024-01-02", "1000.00"));
        Map<String, Object> resgate = new java.util.HashMap<>();
        resgate.put("ticker", CatalogIds.CDB);
        resgate.put("type", "SELL");
        resgate.put("transactionDate", "2024-01-16");
        // liquido acima do disponivel, lancado mesmo assim: o resgate prova o valor (D4; PLANO_VENDA_RENDA_FIXA, R9)
        resgate.put("totalAmount", new BigDecimal("1500.00"));
        resgate.put("noteId", compra.noteId());
        resgate.put("isFullSettlement", false);
        TransactionResponseDTO venda = lancarComSucesso(dono, resgate);
        TransactionResponseDTO gravada = transacoesDe(dono).stream().filter(t -> t.id().equals(venda.id()))
            .findFirst().orElseThrow();
        assertThat(gravada.netAmount()).isEqualByComparingTo("1500.00");
        // o bruto e o liquido mais o IR e o IOF do rendimento da curva
        assertThat(gravada.totalAmount()).isEqualByComparingTo(
            gravada.netAmount().add(gravada.incomeTax()).add(gravada.iofTax()));
        assertThat(gravada.totalAmount()).isGreaterThan(new BigDecimal("1500.00"));

        Map<String, Object> p = posicaoDe(dono, CatalogIds.CDB);
        // sobra o centavo da regra F, rendendo de 16 a 31 de janeiro (12 dias uteis)
        assertThat(numero(p.get("currentValue")).doubleValue()).isCloseTo(0.01, within(0.001));
        assertThat(numero(p.get("valorization")))
            .isEqualByComparingTo(gravada.totalAmount().subtract(new BigDecimal("1000.00")).add(new BigDecimal("0.01")));

        assertThat(delete(dono, "/api/v1/transactions/" + venda.id()).getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> depois = posicaoDe(dono, CatalogIds.CDB);
        assertThat(numero(depois.get("currentValue")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 22), within(0.01));
    }

    private Map<String, Object> resgateLiquido(Object noteId, String data, String liquido) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("ticker", CatalogIds.CDB);
        r.put("type", "SELL");
        r.put("transactionDate", data);
        r.put("totalAmount", new BigDecimal(liquido));
        r.put("noteId", noteId);
        r.put("isFullSettlement", false);
        return r;
    }

    @Test
    @DisplayName("Previa da venda pelo liquido (PLANO_VENDA_RENDA_FIXA, R9): bruto = liquido + IR + IOF; acima do "
        + "disponivel ela avisa, e nada e gravado")
    @SuppressWarnings("unchecked")
    void previaDaVenda() {
        TransactionResponseDTO compra = lancarComSucesso(dono, cdb100("2024-01-02", "1000.00"));

        Map<String, Object> dentro = post(dono, "/api/v1/transactions/sale-preview",
            resgateLiquido(compra.noteId(), "2024-01-16", "500.00"), Map.class).getBody();
        assertThat(dentro.get("aboveAvailable")).isEqualTo(false);
        assertThat(numero(dentro.get("net"))).isEqualByComparingTo("500.00");
        assertThat(numero(dentro.get("gross"))).isEqualByComparingTo(
            numero(dentro.get("net")).add(numero(dentro.get("ir"))).add(numero(dentro.get("iof"))));
        assertThat(numero(dentro.get("gross"))).isGreaterThan(new BigDecimal("500.00"));
        // 10 dias uteis de CDI ate 16/01: bruto 1000 x 1,0005^10; o liquido disponivel tira IOF (14 dias) e IR
        assertThat(numero(dentro.get("grossBefore")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 10), within(0.01));
        assertThat(numero(dentro.get("availableNet"))).isLessThan(numero(dentro.get("grossBefore")));
        assertThat((java.util.List<Object>) dentro.get("lots")).hasSize(1);

        Map<String, Object> acima = post(dono, "/api/v1/transactions/sale-preview",
            resgateLiquido(compra.noteId(), "2024-01-16", "2000.00"), Map.class).getBody();
        assertThat(acima.get("aboveAvailable")).isEqualTo(true);
        assertThat(transacoesDe(dono)).hasSize(1);
    }

    @Test
    @DisplayName("Recalculo em cascata (R10): um aporte retroativo muda o bruto, o IR e o IOF da venda ja gravada; o "
        + "liquido digitado fica")
    void cascataRetroativa() {
        TransactionResponseDTO compra = lancarComSucesso(dono, cdb100("2024-01-02", "1000.00"));
        TransactionResponseDTO venda = lancarComSucesso(dono, resgateLiquido(compra.noteId(), "2024-01-16", "500.00"));
        TransactionResponseDTO antes = transacoesDe(dono).stream().filter(t -> t.id().equals(venda.id()))
            .findFirst().orElseThrow();

        // aporte retroativo em 12/01: um lote de 4 dias (IOF 86%) entra na proporcao da venda de 16/01
        Map<String, Object> aporte = cdb100("2024-01-12", "1000.00");
        aporte.put("noteId", compra.noteId());
        lancarComSucesso(dono, aporte);

        TransactionResponseDTO depois = transacoesDe(dono).stream().filter(t -> t.id().equals(venda.id()))
            .findFirst().orElseThrow();
        assertThat(depois.netAmount()).isEqualByComparingTo("500.00");
        assertThat(depois.totalAmount()).isEqualByComparingTo(
            depois.netAmount().add(depois.incomeTax()).add(depois.iofTax()));
        assertThat(depois.iofTax()).isNotEqualByComparingTo(antes.iofTax());
        assertThat(depois.totalAmount()).isNotEqualByComparingTo(antes.totalAmount());
    }

    @Test
    @DisplayName("Indice novo: o aviso INDICES leva a curva ate o novo ultimo CDI")
    void avisoDeIndice() {
        lancarComSucesso(dono, cdb100("2024-01-02", "1000.00"));
        cdi(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));

        positionRefreshService.refresh(CatalogChange.indices(Set.of("CDI")));

        Map<String, Object> p = posicaoDe(dono, CatalogIds.CDB);
        int fev = BusinessCalendar.businessDaysBetween(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));
        assertThat(numero(p.get("currentValue")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 22 + fev), within(0.01));
        assertThat(p).containsEntry("valuationDate", "2024-02-29");
    }

    @Test
    @DisplayName("Vencido: para de render no vencimento e fica na carteira com o selo, à espera da liquidação do usuário "
        + "(docs/planos/PLANO_VENDA_RENDA_FIXA.md, seção 6)")
    @SuppressWarnings("unchecked")
    void vencido() {
        Map<String, Object> c = cdb100("2024-01-02", "1000.00");
        c.put("maturityDate", "2024-01-15");
        lancarComSucesso(dono, c);

        // o sistema nao lanca venda: o titulo fica aberto, vencido, valendo o saldo no vencimento
        assertThat(transacoesDe(dono)).noneMatch(t -> t.type() == com.primevest.domain.transaction.TransactionType.SELL);
        Map<String, Object> p = posicaoDe(dono, CatalogIds.CDB);
        assertThat(p).containsEntry("matured", true);
        double bruto = 1000 * Math.pow(1.0005, 9);
        assertThat(numero(p.get("currentValue")).doubleValue()).isCloseTo(bruto, within(0.01));
        // liquido estimado: o rendimento paga IOF (13 dias, 56%) e IR (22,5% sobre o resto)
        assertThat(numero(p.get("estimatedNet")).doubleValue()).isLessThan(bruto).isGreaterThan(1000);
        java.util.List<Map<String, Object>> lotes = (java.util.List<Map<String, Object>>) p.get("lots");
        assertThat(lotes).hasSize(1);
        assertThat(numero(lotes.get(0).get("net"))).isEqualByComparingTo(numero(p.get("estimatedNet")));
    }

    @Test
    @DisplayName("TR nao tem curva: valor fica no aplicado e a tela recebe o motivo")
    void semCurva() {
        Map<String, Object> c = cdb100("2024-01-02", "1000.00");
        c.put("rateType", "HIBRIDO");
        c.put("indexer", "TR");
        c.put("indexerRate", new BigDecimal("2"));
        lancarComSucesso(dono, c);

        Map<String, Object> p = posicaoDe(dono, CatalogIds.CDB);
        assertThat(numero(p.get("currentValue"))).isEqualByComparingTo("1000.00");
        assertThat(p).containsEntry("valuationBasis", "SEM_CURVA");
        assertThat((String) p.get("valuationNote")).contains("TR");
    }

    @Test
    @DisplayName("Tesouro pelo preco oficial: cotas x PU de resgate − custo, com a data do preco")
    void tesouroMercado() {
        jdbc.update("UPDATE assets SET current_price = 1000.00 WHERE ticker = ?", CatalogIds.TESOURO_IPCA_2050);
        jdbc.update("INSERT INTO asset_price_history (asset_id, reference_date, close_price, source) "
                + "SELECT id, DATE '2026-10-02', 1000.00, 'TESOURO' FROM assets WHERE ticker = ?", CatalogIds.TESOURO_IPCA_2050);
        Map<String, Object> compra = lancamento(CatalogIds.TESOURO_IPCA_2050, "BUY", "2026-09-01", "2", "830.07");
        compra.put("indexerRate", new BigDecimal("7.0900"));
        lancarComSucesso(dono, compra);

        Map<String, Object> p = posicaoDe(dono, CatalogIds.TESOURO_IPCA_2050);
        assertThat(numero(p.get("valorization"))).isEqualByComparingTo("339.86");
        assertThat(p).containsEntry("valuationBasis", "MERCADO").containsEntry("valuationDate", "2026-10-02");
        // 31 dias: sem IOF; IR 22,5% de 339,86 = 76,46 (truncado) — liquido 2000,00 − 76,46
        assertThat(numero(p.get("estimatedNet"))).isEqualByComparingTo("1923.54");
    }
}
