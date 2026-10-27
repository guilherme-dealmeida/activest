package com.activest.functional;

import com.activest.domain.transaction.dto.TransactionResponseDTO;
import com.activest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static com.activest.functional.FundTransactionsIT.createFund;
import static com.activest.functional.FundTransactionsIT.fundEntry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Imposto dos fundos (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, F7: C7–C10, D17, D21, D22), com as alíquotas da V12.
 * Come-cotas de 29/05/2026 (último dia útil de maio) com a cota da série; o de novembro ainda não chegou.
 */
class FundTaxesIT extends FunctionalBase {

    static final String LONG_TERM = "99999999000272";
    static final String PENSION = "99999999000353";

    private User owner;

    @BeforeEach
    void scenario() {
        Long lt = createFund(jdbc, LONG_TERM, "Renda Fixa", true);
        Long pension = createFund(jdbc, PENSION, "Previdência", false);
        for (Long id : new Long[]{lt, pension}) {
            jdbc.update("DELETE FROM asset_price_history WHERE asset_id = ?", id);
            jdbc.update("INSERT INTO asset_price_history (asset_id, reference_date, close_price, source) VALUES (?, ?, 12, 'CVM')",
                id, Date.valueOf(LocalDate.of(2026, 5, 29)));
            jdbc.update("UPDATE assets SET current_price = 12 WHERE id = ?", id);
        }
        owner = userWithPortfolio("fundo-ir-" + System.nanoTime() + "@activest.test");
    }

    private Map<String, Object> fundEvent(String ticker) {
        return jdbc.queryForMap("""
            SELECT e.event_date, e.quote, e.base_quote, e.shares_before, e.shares_withheld, e.tax_amount, e.rate
              FROM fund_tax_events e JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ?""", ticker);
    }

    @Test
    @DisplayName("C9/D22: come-cotas de maio (15% no longo prazo) gravado por lote e as cotas retidas saem da posição")
    void comeCotas() {
        recordSuccessfully(owner, fundEntry(LONG_TERM, "BUY", "2026-01-05", "1000.00", "10"));   // 100 cotas

        Map<String, Object> e = fundEvent(LONG_TERM);
        assertThat(e.get("event_date").toString()).isEqualTo("2026-05-29");
        assertThat((BigDecimal) e.get("tax_amount")).isEqualByComparingTo("30.00");      // (12 - 10) x 100 x 15%
        assertThat((BigDecimal) e.get("shares_withheld")).isEqualByComparingTo("2.5");   // 30 / 12
        assertThat((BigDecimal) e.get("rate")).isEqualByComparingTo("0.15");

        Map<String, Object> position = positionOf(owner, LONG_TERM);
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("97.5");
        assertThat(number(position.get("totalInvested"))).isEqualByComparingTo("1000.00");
        // C10: líquido estimado de um resgate total hoje — bruto menos o IR que falta (o come-cotas já foi retido)
        BigDecimal gross = number(position.get("currentValue"));
        assertThat(gross).isEqualByComparingTo("1170.00");
        BigDecimal net = number(position.get("estimatedNet"));
        assertThat(net).isLessThan(gross).isGreaterThanOrEqualTo(gross.subtract(new BigDecimal("39.00")));
    }

    @Test
    @DisplayName("C7: resgate por PEPS — IR do prazo sobre o ganho menos o come-cotas já retido daquelas cotas")
    void redemptionAfterComeCotas() {
        recordSuccessfully(owner, fundEntry(LONG_TERM, "BUY", "2026-01-05", "1000.00", "10"));
        // prévia do formulário: os mesmos números, sem gravar
        @SuppressWarnings("unchecked")
        Map<String, Object> preview = post(owner, "/api/v1/transactions/sale-preview",
            fundEntry(LONG_TERM, "SELL", "2026-06-15", "600.00", "12"), Map.class).getBody();
        assertThat(number(preview.get("gross"))).isEqualByComparingTo("600.00");
        assertThat(number(preview.get("ir"))).isEqualByComparingTo("7.11");
        assertThat(number(preview.get("net"))).isEqualByComparingTo("592.89");
        assertThat(preview.get("saleRule")).isEqualTo("PEPS");

        TransactionResponseDTO sale = recordSuccessfully(owner, fundEntry(LONG_TERM, "SELL", "2026-06-15", "600.00", "12"));
        TransactionResponseDTO saved = transactionsOf(owner).stream().filter(t -> t.id().equals(sale.id())).findFirst()
            .orElseThrow();
        // 50 cotas; ganho 2 x 50 = 100; 161 dias = 22,5% -> 22,50; já retido 30 x 50 / 97,5 = 15,38 -> IR 7,11
        assertThat(saved.shares()).isEqualByComparingTo("50");
        assertThat(saved.iofTax()).isEqualByComparingTo("0");
        assertThat(saved.incomeTax()).isEqualByComparingTo("7.11");
        assertThat(saved.netAmount()).isEqualByComparingTo("592.89");
        assertThat(number(positionOf(owner, LONG_TERM).get("totalShares"))).isEqualByComparingTo("47.5");
    }

    @Test
    @DisplayName("IOF nos 30 primeiros dias: resgate em 10 dias paga IOF (66%) e IR sobre o que sobra do ganho")
    void iofWithin30Days() {
        recordSuccessfully(owner, fundEntry(LONG_TERM, "BUY", "2026-02-02", "1000.00", "10"));
        TransactionResponseDTO sale = recordSuccessfully(owner, fundEntry(LONG_TERM, "SELL", "2026-02-12", "505.00", "10.10"));
        TransactionResponseDTO saved = transactionsOf(owner).stream().filter(t -> t.id().equals(sale.id())).findFirst()
            .orElseThrow();
        // 50 cotas; ganho 0,10 x 50 = 5,00; IOF 66% = 3,30; IR 22,5% x 1,70 = 0,38
        assertThat(saved.iofTax()).isEqualByComparingTo("3.30");
        assertThat(saved.incomeTax()).isEqualByComparingTo("0.38");
        assertThat(saved.netAmount()).isEqualByComparingTo("501.32");
    }

    @Test
    @DisplayName("D21: Previdência vale bruto — sem come-cotas, resgate sem IR/IOF e líquido estimado = bruto")
    void pension() {
        recordSuccessfully(owner, fundEntry(PENSION, "BUY", "2026-01-05", "1000.00", "10"));
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM fund_tax_events e JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ?""",
            Integer.class, PENSION)).isZero();
        Map<String, Object> position = positionOf(owner, PENSION);
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("100");
        assertThat(number(position.get("estimatedNet"))).isEqualByComparingTo(number(position.get("currentValue")));
        assertThat(position.get("valuationNote").toString()).contains("IR do plano");

        TransactionResponseDTO sale = recordSuccessfully(owner, fundEntry(PENSION, "SELL", "2026-06-15", "600.00", "12"));
        TransactionResponseDTO saved = transactionsOf(owner).stream().filter(t -> t.id().equals(sale.id())).findFirst()
            .orElseThrow();
        assertThat(saved.incomeTax()).isEqualByComparingTo("0");
        assertThat(saved.netAmount()).isEqualByComparingTo("600.00");
    }
}
