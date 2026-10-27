package com.activest.adm.domain.currency;

import com.activest.domain.currency.FiatCurrencies;
import com.activest.domain.user.User;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tela de administrador "Moedas" (docs/planos/PLANO_CADASTRO_MOEDAS.md, D13 e 10.4). */
class CurrencyAdminIT extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/currencies";

    private User admin;
    private User common;

    @BeforeEach
    void users() {
        admin = userWithPortfolio("admin-moedas-" + System.nanoTime() + "@activest.test");
        jdbc.update("UPDATE users SET is_admin = TRUE WHERE id = ?", admin.getId());
        common = userWithPortfolio("comum-moedas-" + System.nanoTime() + "@activest.test");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM currencies WHERE code = 'CHF'");
        jdbc.update("UPDATE currencies SET name = 'Dólar americano', active = TRUE WHERE code = 'USD'");
    }

    private Map<String, Object> body(String code, String rateSource, boolean active) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", "Franco suíço");
        b.put("symbol", "CHF");
        b.put("decimals", 2);
        b.put("rateSource", rateSource);
        b.put("active", active);
        return b;
    }

    @Test
    @DisplayName("Só administrador; lista com a base primeiro; moeda nova entra nas moedas da importação")
    @SuppressWarnings("unchecked")
    void crud() {
        assertThat(get(common, BASE, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        List<Map<String, Object>> list = get(admin, BASE, List.class).getBody();
        assertThat(list).extracting(m -> m.get("code")).startsWith("BRL").contains("USD");

        ResponseEntity<Map> created = post(admin, BASE, body("chf", "BCB_PTAX", false), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody().get("code")).isEqualTo("CHF");
        assertThat(FiatCurrencies.codes()).contains("CHF", "EUR");
        assertThat(post(admin, BASE, body("CHF", "BCB_PTAX", false), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(admin, BASE, body("GBP", "NONE", false), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        Map<String, Object> upd = body(null, "NONE", false);
        upd.remove("code");
        assertThat(put(admin, BASE + "/BRL", upd, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY); // base nao desativa

        Map<String, Object> usd = body(null, "BCB_PTAX", true);
        usd.remove("code");
        usd.put("name", "Dólar dos EUA");
        usd.put("symbol", "US$");
        ResponseEntity<Map> updated = put(admin, BASE + "/USD", usd, Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name")).isEqualTo("Dólar dos EUA");
    }
}
