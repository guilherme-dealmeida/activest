package com.activest.domain.currency;

import com.activest.exception.BusinessException;
import com.activest.support.JpaTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V9: cadastro de moedas, câmbio, regras do exterior e o {@link ExchangeRateService} (PLANO_CADASTRO_MOEDAS, M1). */
class CurrencyRepositoryTest extends JpaTestBase {

    @Autowired private CurrencyRepository currencies;
    @Autowired private ExchangeRateRepository rates;
    @Autowired private ForeignTaxRuleRepository taxRules;
    @Autowired private JdbcTemplate jdbc;

    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM exchange_rates");
        service = new ExchangeRateService(rates, currencies);
    }

    private void rate(LocalDate d, String buy, String sell) {
        jdbc.update("INSERT INTO exchange_rates (currency_code, reference_date, buy_rate, sell_rate) VALUES ('USD', ?, ?, ?)",
            Date.valueOf(d), new BigDecimal(buy), new BigDecimal(sell));
    }

    @Test
    @DisplayName("Seed: BRL é a única base (sem fonte), USD com PTAX; tipos e stablecoins com moeda e região")
    void seed() {
        assertThat(currencies.findByBaseTrue().orElseThrow().getCode()).isEqualTo("BRL");
        Currency usd = currencies.findById("USD").orElseThrow();
        assertThat(usd.getSymbol()).isEqualTo("US$");
        assertThat(usd.getRateSource()).isEqualTo(CurrencyRateSource.BCB_PTAX);
        assertThat(currencies.findByActiveTrueAndRateSourceOrderByCode(CurrencyRateSource.BCB_PTAX))
            .extracting(Currency::getCode).containsExactly("USD");

        assertThat(jdbc.queryForObject("SELECT default_currency_code || '/' || region FROM asset_types WHERE name = 'STOCK'",
            String.class)).isEqualTo("USD/EXTERIOR");
        assertThat(jdbc.queryForObject("SELECT default_currency_code || '/' || region FROM asset_types WHERE name = 'BDR'",
            String.class)).isEqualTo("BRL/EXTERIOR");
        assertThat(jdbc.queryForObject("SELECT region FROM asset_types WHERE name = 'CRIPTO'", String.class))
            .isEqualTo("CRIPTO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE currency_code <> 'BRL'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT reference_currency_code FROM assets WHERE ticker = 'USDT'", String.class))
            .isEqualTo("USD");
    }

    @Test
    @DisplayName("Só uma moeda base")
    void singleBase() {
        assertThatThrownBy(() -> jdbc.update("UPDATE currencies SET is_base = TRUE, rate_source = 'NONE' WHERE code = 'USD'"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("A base não pode ficar inativa")
    void baseActive() {
        assertThatThrownBy(() -> jdbc.update("UPDATE currencies SET active = FALSE WHERE code = 'BRL'"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("A base não tem fonte de câmbio")
    void baseNoSource() {
        assertThatThrownBy(() -> jdbc.update("UPDATE currencies SET rate_source = 'BCB_PTAX' WHERE code = 'BRL'"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("C1: câmbio do dia; sem boletim usa o último anterior (sem limite, D27); antes da série, vazio")
    void rateOn() {
        rate(LocalDate.of(2026, 10, 22), "5.4000", "5.4006");
        rate(LocalDate.of(2026, 10, 23), "5.3800", "5.3806");

        ExchangeRateService.Quote fri = service.rateOn("USD", LocalDate.of(2026, 10, 23), RateSide.SELL).orElseThrow();
        assertThat(fri.rate()).isEqualByComparingTo("5.3806");
        assertThat(fri.carried()).isFalse();

        ExchangeRateService.Quote sun = service.rateOn("USD", LocalDate.of(2026, 10, 25), RateSide.BUY).orElseThrow();
        assertThat(sun.rate()).isEqualByComparingTo("5.3800");
        assertThat(sun.rateDate()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(sun.carried()).isTrue();

        // muito tempo depois continua carregando (nunca indisponivel)
        assertThat(service.rateOn("USD", LocalDate.of(2027, 3, 1), RateSide.SELL).orElseThrow().rateDate())
            .isEqualTo(LocalDate.of(2026, 10, 23));

        assertThat(service.rateOn("USD", LocalDate.of(2026, 10, 21), RateSide.SELL)).isEmpty();
        assertThatThrownBy(() -> service.requireRateOn("USD", LocalDate.of(2026, 10, 21), RateSide.SELL))
            .isInstanceOf(BusinessException.class).hasMessageContaining("21/10/2026");
    }

    @Test
    @DisplayName("Moeda base: câmbio 1 e o próprio valor; conversão arredonda HALF_EVEN (D26)")
    void toBase() {
        rate(LocalDate.of(2026, 10, 23), "5.0000", "5.12345678");
        assertThat(service.isBase("BRL")).isTrue();
        assertThat(service.rateOn("BRL", LocalDate.of(2000, 1, 1), RateSide.SELL).orElseThrow().rate())
            .isEqualByComparingTo("1");
        assertThat(service.toBase(new BigDecimal("10.005"), "BRL", LocalDate.now(), RateSide.SELL, 2))
            .isEqualByComparingTo("10.00");
        assertThat(service.toBase(new BigDecimal("100"), "USD", LocalDate.of(2026, 10, 23), RateSide.SELL, 2))
            .isEqualByComparingTo("512.35");
        assertThat(service.series("USD", RateSide.BUY).rateOn(LocalDate.of(2026, 10, 24))).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Regras do exterior: vigência por data e país (P1, 9.4)")
    void taxRules() {
        var all = taxRules.findAllOrdered();
        assertThat(ForeignTaxRuleService.select(all, ForeignTax.IOF_FX, ForeignTaxEvent.REMITTANCE_OWN_ACCOUNT, null, null,
            LocalDate.of(2025, 5, 22)).orElseThrow().getRate()).isEqualByComparingTo("0.011");
        assertThat(ForeignTaxRuleService.select(all, ForeignTax.IOF_FX, ForeignTaxEvent.REMITTANCE_OWN_ACCOUNT, null, null,
            LocalDate.of(2025, 5, 23)).orElseThrow().getRate()).isEqualByComparingTo("0.035");
        assertThat(ForeignTaxRuleService.select(all, ForeignTax.WITHHOLDING_FOREIGN, ForeignTaxEvent.DIVIDEND, "US", 8L,
            LocalDate.of(2026, 1, 2)).orElseThrow().getRate()).isEqualByComparingTo("0.30");
        assertThat(ForeignTaxRuleService.select(all, ForeignTax.WITHHOLDING_FOREIGN, ForeignTaxEvent.DIVIDEND, "IE", 8L,
            LocalDate.of(2026, 1, 2))).isEmpty();
        assertThat(ForeignTaxRuleService.select(all, ForeignTax.IR_FOREIGN, ForeignTaxEvent.SALE, "US", 8L,
            LocalDate.of(2023, 12, 31)).orElseThrow().getBrackets()).contains("monthlyExemption");
        assertThat(ForeignTaxRuleService.select(all, ForeignTax.IR_FOREIGN, ForeignTaxEvent.SALE, "US", 8L,
            LocalDate.of(2024, 1, 1)).orElseThrow().getBrackets()).isNull();
    }
}
