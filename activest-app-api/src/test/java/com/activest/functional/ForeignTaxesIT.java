package com.activest.functional;

import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.FxOperationService;
import com.activest.domain.currency.FxPurpose;
import com.activest.domain.currency.dto.FxOperationDTOs;
import com.activest.domain.transaction.dto.TransactionResponseDTO;
import com.activest.domain.user.User;
import com.activest.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tributos do exterior (docs/planos/PLANO_CADASTRO_MOEDAS.md, M4): IR estimado na venda (C5), provento com retenção
 * dos EUA e IR brasileiro (C6) e IOF da operação de câmbio (C7), por vigência.
 */
class ForeignTaxesIT extends FunctionalBase {

    static final String USD_ASSET = "ZZUSD";

    @Autowired private ExchangeRateService exchangeRates;
    @Autowired private FxOperationService fxOperations;

    private User owner;

    @BeforeEach
    void scenario() {
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("INSERT INTO exchange_rates (currency_code, reference_date, buy_rate, sell_rate) VALUES "
            + "('USD', ?, 4.9900, 5.0000), ('USD', ?, 5.1900, 5.2000), ('USD', ?, 5.2900, 5.3000)",
            Date.valueOf(LocalDate.of(2026, 9, 1)), Date.valueOf(LocalDate.of(2026, 9, 2)),
            Date.valueOf(LocalDate.of(2026, 9, 15)));
        exchangeRates.evict();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = ?", Long.class, USD_ASSET) == 0) {
            jdbc.update("INSERT INTO assets (ticker, name, asset_type_id, currency_code, current_price, active) "
                + "VALUES (?, 'Fictício em dólar', (SELECT id FROM asset_types WHERE name = 'STOCK'), 'USD', 110, true)",
                USD_ASSET);
        }
        jdbc.update("DELETE FROM dividends WHERE cash_event_id IN (SELECT e.id FROM asset_cash_events e JOIN assets a "
            + "ON a.id = e.asset_id WHERE a.ticker = ?)", USD_ASSET);
        jdbc.update("DELETE FROM asset_cash_events WHERE asset_id = (SELECT id FROM assets WHERE ticker = ?)", USD_ASSET);
        owner = userWithPortfolio("tributo-" + System.nanoTime() + "@activest.test");
    }

    private Map<String, Object> usd(String type, String date, String shares, String price, String fxRate) {
        Map<String, Object> body = entry(USD_ASSET, type, date, shares, price);
        if (fxRate != null) {
            body.put("fxRate", new BigDecimal(fxRate));
        }
        return body;
    }

    @Test
    @DisplayName("C5 desde 2024: 15% sobre o ganho em reais, com a variação cambial; líquido = total − IR")
    void saleSince2024() {
        recordSuccessfully(owner, usd("BUY", "2026-09-01", "10", "100", null));        // 5.000,00
        TransactionResponseDTO sale = recordSuccessfully(owner, usd("SELL", "2026-09-02", "10", "120", null)); // 6.240,00

        assertThat(sale.totalAmount()).isEqualByComparingTo("6240.00");
        assertThat(sale.incomeTax()).isEqualByComparingTo("186.00");
        assertThat(sale.netAmount()).isEqualByComparingTo("6054.00");
        assertThat(sale.iofTax()).isEqualByComparingTo("0");

        // so a variacao cambial (mesmo preco em US$) tambem e ganho
        recordSuccessfully(owner, usd("BUY", "2026-09-01", "10", "100", null));
        TransactionResponseDTO fx = recordSuccessfully(owner, usd("SELL", "2026-09-02", "10", "100", null));
        assertThat(fx.incomeTax()).isEqualByComparingTo("30.00"); // (5.200 − 5.000) × 15%

        // prejuizo: IR 0
        recordSuccessfully(owner, usd("BUY", "2026-09-02", "10", "100", null));
        TransactionResponseDTO loss = recordSuccessfully(owner, usd("SELL", "2026-09-02", "10", "90", null));
        assertThat(loss.incomeTax()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("C5 antes de 2024 (D23): vendas do mês até R$ 35 mil isentas; acima, 15% sobre o ganho")
    void saleBefore2024() {
        recordSuccessfully(owner, usd("BUY", "2023-01-02", "100", "100", "5"));        // 50.000
        TransactionResponseDTO small = recordSuccessfully(owner, usd("SELL", "2023-02-01", "10", "110", "5")); // 5.500
        assertThat(small.incomeTax()).isEqualByComparingTo("0");

        TransactionResponseDTO big = recordSuccessfully(owner, usd("SELL", "2023-03-01", "80", "110", "5")); // 44.000
        assertThat(big.incomeTax()).isEqualByComparingTo("600.00"); // ganho 4.000 × 15%
    }

    @Test
    @DisplayName("C6: provento em US$ × PTAX compra do pagamento; retenção de 30% dos EUA; IR brasileiro 0")
    void dividend() {
        jdbc.update("INSERT INTO asset_cash_events (type, label, data_com, payment_date, value_per_share, source, asset_id) "
            + "VALUES ('DIVIDENDO', 'DIVIDENDO', ?, ?, 1.5, 'B3', (SELECT id FROM assets WHERE ticker = ?))",
            Date.valueOf(LocalDate.of(2026, 9, 10)), Date.valueOf(LocalDate.of(2026, 9, 15)), USD_ASSET);
        recordSuccessfully(owner, usd("BUY", "2026-09-01", "10", "100", null));

        Map<String, Object> d = jdbc.queryForMap("SELECT d.* FROM dividends d JOIN portfolio_assets p "
            + "ON p.id = d.portfolio_asset_id JOIN portfolios f ON f.id = p.portfolio_id WHERE f.user_id = ?", owner.getId());
        assertThat(d.get("currency_code")).isEqualTo("USD");
        assertThat((BigDecimal) d.get("fx_rate")).isEqualByComparingTo("5.29");           // compra de 15/09
        assertThat((BigDecimal) d.get("original_amount_per_share")).isEqualByComparingTo("1.5");
        assertThat((BigDecimal) d.get("original_total_amount")).isEqualByComparingTo("15.00");
        assertThat((BigDecimal) d.get("total_amount")).isEqualByComparingTo("79.35");      // 1,5 × 5,29 × 10
        assertThat((BigDecimal) d.get("foreign_withholding_tax")).isEqualByComparingTo("23.80"); // 23,805 HALF_EVEN
        assertThat((BigDecimal) d.get("foreign_income_tax")).isEqualByComparingTo("0");
        assertThat((BigDecimal) d.get("net_amount")).isEqualByComparingTo("55.55");
        assertThat(d.get("withholding_rule_id")).isNull();
    }

    @Test
    @DisplayName("C7: IOF da remessa pela finalidade e data (1,1% → 3,5% em 23/05/2025); câmbio efetivo")
    void fxOperation() {
        FxOperationDTOs.Response own = fxOperations.create(owner.getId(), new FxOperationDTOs.Request(
            LocalDate.of(2025, 6, 2), "BRL", "USD", new BigDecimal("10000"), new BigDecimal("1900"),
            FxPurpose.OWN_ACCOUNT, BigDecimal.ZERO, "Corretora X", null));
        assertThat(own.iofAmount()).isEqualByComparingTo("350.00");
        assertThat(own.effectiveRate()).isEqualByComparingTo("5.07894737");

        FxOperationDTOs.Response before = fxOperations.create(owner.getId(), new FxOperationDTOs.Request(
            LocalDate.of(2025, 5, 22), "BRL", "USD", new BigDecimal("10000"), new BigDecimal("1900"),
            FxPurpose.OWN_ACCOUNT, new BigDecimal("10"), null, null));
        assertThat(before.iofAmount()).isEqualByComparingTo("110.00");

        FxOperationDTOs.Response inv = fxOperations.create(owner.getId(), new FxOperationDTOs.Request(
            LocalDate.of(2026, 9, 1), "BRL", "USD", new BigDecimal("5000"), new BigDecimal("990"),
            FxPurpose.INVESTMENT, null, null, null));
        assertThat(inv.iofAmount()).isEqualByComparingTo("55.00");
        assertThat(fxOperations.list(owner.getId())).hasSize(3);

        assertThatThrownBy(() -> fxOperations.create(owner.getId(), new FxOperationDTOs.Request(
            LocalDate.of(2026, 9, 1), "BRL", "USD", BigDecimal.TEN, BigDecimal.ONE, FxPurpose.INFLOW, null, null, null)))
            .isInstanceOf(BusinessException.class);
    }
}
