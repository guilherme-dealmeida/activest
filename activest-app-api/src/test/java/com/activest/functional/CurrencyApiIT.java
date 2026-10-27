package com.activest.functional;

import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Rotas de leitura de moedas e câmbio (docs/planos/PLANO_CADASTRO_MOEDAS.md, 10.4). */
class CurrencyApiIT extends FunctionalBase {

    @Autowired private ExchangeRateService exchangeRates;

    private User user;

    @BeforeEach
    void scenario() {
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("INSERT INTO exchange_rates (currency_code, reference_date, buy_rate, sell_rate) VALUES "
            + "('USD', ?, 4.9900, 5.0000)", Date.valueOf(LocalDate.of(2026, 9, 4)));
        exchangeRates.evict();
        user = userWithPortfolio("moedas-api-" + System.nanoTime() + "@activest.test");
    }

    @Test
    @DisplayName("Moedas ativas, câmbio padrão da data (carregado no fim de semana), série e regras do exterior")
    @SuppressWarnings("unchecked")
    void read() {
        List<Map<String, Object>> list = get(user, "/api/v1/currencies", List.class).getBody();
        assertThat(list).extracting(m -> m.get("code")).containsExactly("BRL", "USD");
        assertThat(list.get(1).get("symbol")).isEqualTo("US$");

        Map<String, Object> rate = get(user, "/api/v1/currencies/usd/rate?date=2026-09-06", Map.class).getBody();
        assertThat(number(rate.get("sell"))).isEqualByComparingTo("5");
        assertThat(rate.get("rateDate")).isEqualTo("2026-09-04");
        assertThat(rate.get("carried")).isEqualTo(true);
        assertThat(get(user, "/api/v1/currencies/USD/rate?date=2026-01-01", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(get(user, "/api/v1/currencies/USD/rates?from=2026-09-01&to=2026-09-30", List.class).getBody())
            .hasSize(1);
        assertThat(get(user, "/api/v1/foreign-tax-rules", List.class).getBody()).hasSize(10);
    }

    @Test
    @DisplayName("Operações de câmbio: cria com IOF, lista só as do usuário, edita e exclui")
    @SuppressWarnings("unchecked")
    void fxOperations() {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("operationDate", "2026-09-04",
            "fromCurrencyCode", "BRL", "toCurrencyCode", "USD", "amountSent", 5000, "amountReceived", 990,
            "purpose", "INVESTMENT"));
        ResponseEntity<Map> created = post(user, "/api/v1/fx-operations", body, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(created.getBody().get("iofAmount"))).isEqualByComparingTo("55.00");
        String id = (String) created.getBody().get("id");

        User other = userWithPortfolio("moedas-outro-" + System.nanoTime() + "@activest.test");
        assertThat(get(other, "/api/v1/fx-operations", List.class).getBody()).isEmpty();
        assertThat(delete(other, "/api/v1/fx-operations/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        body.put("purpose", "OWN_ACCOUNT");
        ResponseEntity<Map> updated = put(user, "/api/v1/fx-operations/" + id, body, Map.class);
        assertThat(number(updated.getBody().get("iofAmount"))).isEqualByComparingTo("175.00");
        assertThat(delete(user, "/api/v1/fx-operations/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
