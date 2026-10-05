package com.primevest.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** C10: com {@code SIGNUP_ENABLED=false} (D16) o cadastro responde 403 e a tela some com o link. */
@SuppressWarnings("rawtypes")
@TestPropertySource(properties = "primevest.auth.signup-enabled=false")
class CadastroDesligadoIT extends FuncionalBase {

    @Test
    @DisplayName("Cadastro desligado: enabled=false e signup/start 403")
    void desligado() {
        assertThat(rest.getForObject("/api/v1/auth/signup/enabled", Map.class)).containsEntry("enabled", false);
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/auth/signup/start", Map.of(
            "name", "X", "email", "x@primevest.test", "password", "senha-forte-123", "acceptTerms", true), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
