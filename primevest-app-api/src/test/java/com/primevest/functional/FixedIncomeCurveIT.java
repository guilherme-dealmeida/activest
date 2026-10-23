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
class FixedIncomeCurveIT extends FunctionalBase {

    @Autowired
    PositionRefreshService positionRefreshService;

    private User owner;

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
    void scenario() {
        owner = userWithPortfolio("curva@primevest.test");
        cdi(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));
    }

    @AfterEach
    void clearTreasury() {
        jdbc.update("DELETE FROM asset_price_history WHERE source = 'TESOURO'");
        jdbc.update("UPDATE assets SET current_price = NULL WHERE asset_type_id = "
                + "(SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')");
    }

    private Map<String, Object> cdb100(String data, String value) {
        Map<String, Object> c = cdbInvestment(CatalogIds.CDB, data, value, "BANCO TESTE");
        c.put("indexerRate", new BigDecimal("100"));
        return c;
    }

    @Test
    @DisplayName("CDB 100% do CDI: a compra ja sai valorizada pela curva ate o ultimo CDI (22 dias uteis de janeiro)")
    void curveAtBuy() {
        recordSuccessfully(owner, cdb100("2024-01-02", "1000.00"));

        Map<String, Object> p = positionOf(owner, CatalogIds.CDB);
        assertThat(number(p.get("currentValue")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 22), within(0.01));
        assertThat(p).containsEntry("valuationBasis", "CURVA").containsEntry("valuationDate", "2024-01-31")
                .containsEntry("matured", false);
    }

    @Test
    @DisplayName("Resgate parcial rende so o que ficou; excluir o resgate volta para a curva (PENDENCIAS 10.2)")
    void redemptionAndExclusion() {
        TransactionResponseDTO buy = recordSuccessfully(owner, cdb100("2024-01-02", "1000.00"));
        Map<String, Object> redemption = new java.util.HashMap<>();
        redemption.put("ticker", CatalogIds.CDB);
        redemption.put("type", "SELL");
        redemption.put("transactionDate", "2024-01-16");
        // liquido acima do disponivel, lancado mesmo assim: o resgate prova o valor (D4; PLANO_VENDA_RENDA_FIXA, R9)
        redemption.put("totalAmount", new BigDecimal("1500.00"));
        redemption.put("noteId", buy.noteId());
        redemption.put("isFullSettlement", false);
        TransactionResponseDTO sale = recordSuccessfully(owner, redemption);
        TransactionResponseDTO stored = transactionsOf(owner).stream().filter(t -> t.id().equals(sale.id()))
            .findFirst().orElseThrow();
        assertThat(stored.netAmount()).isEqualByComparingTo("1500.00");
        // o bruto e o liquido mais o IR e o IOF do rendimento da curva
        assertThat(stored.totalAmount()).isEqualByComparingTo(
            stored.netAmount().add(stored.incomeTax()).add(stored.iofTax()));
        assertThat(stored.totalAmount()).isGreaterThan(new BigDecimal("1500.00"));

        Map<String, Object> p = positionOf(owner, CatalogIds.CDB);
        // sobra o centavo da regra F, rendendo de 16 a 31 de janeiro (12 dias uteis)
        assertThat(number(p.get("currentValue")).doubleValue()).isCloseTo(0.01, within(0.001));
        assertThat(number(p.get("valorization")))
            .isEqualByComparingTo(stored.totalAmount().subtract(new BigDecimal("1000.00")).add(new BigDecimal("0.01")));

        assertThat(delete(owner, "/api/v1/transactions/" + sale.id()).getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> after = positionOf(owner, CatalogIds.CDB);
        assertThat(number(after.get("currentValue")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 22), within(0.01));
    }

    private Map<String, Object> netRedemption(Object noteId, String data, String net) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("ticker", CatalogIds.CDB);
        r.put("type", "SELL");
        r.put("transactionDate", data);
        r.put("totalAmount", new BigDecimal(net));
        r.put("noteId", noteId);
        r.put("isFullSettlement", false);
        return r;
    }

    @Test
    @DisplayName("Previa da venda pelo liquido (PLANO_VENDA_RENDA_FIXA, R9): bruto = liquido + IR + IOF; acima do "
        + "disponivel ela avisa, e nada e gravado")
    @SuppressWarnings("unchecked")
    void salePreview() {
        TransactionResponseDTO buy = recordSuccessfully(owner, cdb100("2024-01-02", "1000.00"));

        Map<String, Object> inside = post(owner, "/api/v1/transactions/sale-preview",
            netRedemption(buy.noteId(), "2024-01-16", "500.00"), Map.class).getBody();
        assertThat(inside.get("aboveAvailable")).isEqualTo(false);
        assertThat(number(inside.get("net"))).isEqualByComparingTo("500.00");
        assertThat(number(inside.get("gross"))).isEqualByComparingTo(
            number(inside.get("net")).add(number(inside.get("ir"))).add(number(inside.get("iof"))));
        assertThat(number(inside.get("gross"))).isGreaterThan(new BigDecimal("500.00"));
        // 10 dias uteis de CDI ate 16/01: bruto 1000 x 1,0005^10; o liquido disponivel tira IOF (14 dias) e IR
        assertThat(number(inside.get("grossBefore")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 10), within(0.01));
        assertThat(number(inside.get("availableNet"))).isLessThan(number(inside.get("grossBefore")));
        assertThat((java.util.List<Object>) inside.get("lots")).hasSize(1);

        Map<String, Object> above = post(owner, "/api/v1/transactions/sale-preview",
            netRedemption(buy.noteId(), "2024-01-16", "2000.00"), Map.class).getBody();
        assertThat(above.get("aboveAvailable")).isEqualTo(true);
        assertThat(transactionsOf(owner)).hasSize(1);
    }

    @Test
    @DisplayName("Recalculo em cascata (R10): um aporte retroativo muda o bruto, o IR e o IOF da venda ja gravada; o "
        + "liquido digitado fica")
    void retroactiveCascade() {
        TransactionResponseDTO buy = recordSuccessfully(owner, cdb100("2024-01-02", "1000.00"));
        TransactionResponseDTO sale = recordSuccessfully(owner, netRedemption(buy.noteId(), "2024-01-16", "500.00"));
        TransactionResponseDTO before = transactionsOf(owner).stream().filter(t -> t.id().equals(sale.id()))
            .findFirst().orElseThrow();

        // aporte retroativo em 12/01: um lote de 4 dias (IOF 86%) entra na proporcao da venda de 16/01
        Map<String, Object> contribution = cdb100("2024-01-12", "1000.00");
        contribution.put("noteId", buy.noteId());
        recordSuccessfully(owner, contribution);

        TransactionResponseDTO after = transactionsOf(owner).stream().filter(t -> t.id().equals(sale.id()))
            .findFirst().orElseThrow();
        assertThat(after.netAmount()).isEqualByComparingTo("500.00");
        assertThat(after.totalAmount()).isEqualByComparingTo(
            after.netAmount().add(after.incomeTax()).add(after.iofTax()));
        assertThat(after.iofTax()).isNotEqualByComparingTo(before.iofTax());
        assertThat(after.totalAmount()).isNotEqualByComparingTo(before.totalAmount());
    }

    @Test
    @DisplayName("Indice novo: o aviso INDICES leva a curva ate o novo ultimo CDI")
    void indexNotice() {
        recordSuccessfully(owner, cdb100("2024-01-02", "1000.00"));
        cdi(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));

        positionRefreshService.refresh(CatalogChange.indices(Set.of("CDI")));

        Map<String, Object> p = positionOf(owner, CatalogIds.CDB);
        int feb = BusinessCalendar.businessDaysBetween(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));
        assertThat(number(p.get("currentValue")).doubleValue()).isCloseTo(1000 * Math.pow(1.0005, 22 + feb), within(0.01));
        assertThat(p).containsEntry("valuationDate", "2024-02-29");
    }

    @Test
    @DisplayName("Vencido: para de render no vencimento e fica na carteira com o selo, à espera da liquidação do usuário "
        + "(docs/planos/PLANO_VENDA_RENDA_FIXA.md, seção 6)")
    @SuppressWarnings("unchecked")
    void matured() {
        Map<String, Object> c = cdb100("2024-01-02", "1000.00");
        c.put("maturityDate", "2024-01-15");
        recordSuccessfully(owner, c);

        // o sistema nao lanca venda: o titulo fica aberto, vencido, valendo o saldo no vencimento
        assertThat(transactionsOf(owner)).noneMatch(t -> t.type() == com.primevest.domain.transaction.TransactionType.SELL);
        Map<String, Object> p = positionOf(owner, CatalogIds.CDB);
        assertThat(p).containsEntry("matured", true);
        double gross = 1000 * Math.pow(1.0005, 9);
        assertThat(number(p.get("currentValue")).doubleValue()).isCloseTo(gross, within(0.01));
        // liquido estimado: o rendimento paga IOF (13 dias, 56%) e IR (22,5% sobre o resto)
        assertThat(number(p.get("estimatedNet")).doubleValue()).isLessThan(gross).isGreaterThan(1000);
        java.util.List<Map<String, Object>> lots = (java.util.List<Map<String, Object>>) p.get("lots");
        assertThat(lots).hasSize(1);
        assertThat(number(lots.get(0).get("net"))).isEqualByComparingTo(number(p.get("estimatedNet")));
    }

    @Test
    @DisplayName("TR nao tem curva: valor fica no aplicado e a tela recebe o motivo")
    void withoutCurve() {
        Map<String, Object> c = cdb100("2024-01-02", "1000.00");
        c.put("rateType", "HIBRIDO");
        c.put("indexer", "TR");
        c.put("indexerRate", new BigDecimal("2"));
        recordSuccessfully(owner, c);

        Map<String, Object> p = positionOf(owner, CatalogIds.CDB);
        assertThat(number(p.get("currentValue"))).isEqualByComparingTo("1000.00");
        assertThat(p).containsEntry("valuationBasis", "SEM_CURVA");
        assertThat((String) p.get("valuationNote")).contains("TR");
    }

    @Test
    @DisplayName("Tesouro pelo preco oficial: cotas x PU de resgate − custo, com a data do preco")
    void treasuryMarket() {
        jdbc.update("UPDATE assets SET current_price = 1000.00 WHERE ticker = ?", CatalogIds.TREASURY_IPCA_2050);
        jdbc.update("INSERT INTO asset_price_history (asset_id, reference_date, close_price, source) "
                + "SELECT id, DATE '2026-10-02', 1000.00, 'TESOURO' FROM assets WHERE ticker = ?", CatalogIds.TREASURY_IPCA_2050);
        Map<String, Object> buy = entry(CatalogIds.TREASURY_IPCA_2050, "BUY", "2026-09-01", "2", "830.07");
        buy.put("indexerRate", new BigDecimal("7.0900"));
        recordSuccessfully(owner, buy);

        Map<String, Object> p = positionOf(owner, CatalogIds.TREASURY_IPCA_2050);
        assertThat(number(p.get("valorization"))).isEqualByComparingTo("339.86");
        assertThat(p).containsEntry("valuationBasis", "MERCADO").containsEntry("valuationDate", "2026-10-02");
        // 31 dias: sem IOF; IR 22,5% de 339,86 = 76,46 (truncado) — liquido 2000,00 − 76,46
        assertThat(number(p.get("estimatedNet"))).isEqualByComparingTo("1923.54");
    }
}
