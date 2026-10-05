package com.primevest.domain.auth.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.auth.AuthSettings;
import com.primevest.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationCodeServiceTest {

    /** Relogio que o teste avanca. */
    static final class Relogio extends Clock {
        Instant agora = Instant.parse("2026-10-05T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
        void avancar(Duration d) { agora = agora.plus(d); }
    }

    private Relogio relogio;
    private InMemoryVerificationStore store;
    private VerificationCodeService service;

    @BeforeEach
    void montar() {
        relogio = new Relogio();
        store = new InMemoryVerificationStore(relogio);
        AuthSettings settings = new AuthSettings(true, 10, 5, 60, 5);
        service = new VerificationCodeService(store, new SecretHasher("chave-de-teste-123456", ""), settings,
            new ObjectMapper(), relogio);
    }

    @Test
    @DisplayName("Codigo certo libera o payload uma vez so; o codigo nao fica em claro no armazenamento")
    void certo() {
        var issued = service.issue("11111111-1111-1111-1111-111111111111", VerificationPurpose.SIGNUP,
            "a@b.c", Map.of("x", "1"), false);
        assertThat(issued.code()).matches("\\d{6}");
        assertThat(store.get("code:11111111-1111-1111-1111-111111111111").orElseThrow()).doesNotContain(issued.code());

        PendingCode p = service.verify(issued.pending().requestId(), VerificationPurpose.SIGNUP, issued.code());
        assertThat(p.get("x")).isEqualTo("1");
        assertThatThrownBy(() -> service.verify(p.requestId(), VerificationPurpose.SIGNUP, issued.code()))
            .isInstanceOf(BusinessException.class).hasMessageContaining("expirou");
    }

    @Test
    @DisplayName("Proposito errado, expirado em 10 min e fachada nunca confirmam")
    void recusas() {
        var issued = service.issue("22222222-2222-2222-2222-222222222222", VerificationPurpose.SIGNUP, "a@b.c", Map.of(), false);
        assertThatThrownBy(() -> service.verify(issued.pending().requestId(), VerificationPurpose.RESET_PASSWORD, issued.code()))
            .isInstanceOf(BusinessException.class);

        relogio.avancar(Duration.ofMinutes(10));
        assertThatThrownBy(() -> service.verify(issued.pending().requestId(), VerificationPurpose.SIGNUP, issued.code()))
            .isInstanceOf(BusinessException.class).hasMessageContaining("expirou");

        var fachada = service.issue("33333333-3333-3333-3333-333333333333", VerificationPurpose.SIGNUP, "a@b.c", Map.of(), true);
        assertThatThrownBy(() -> service.verify(fachada.pending().requestId(), VerificationPurpose.SIGNUP, fachada.code()))
            .isInstanceOf(BusinessException.class).hasMessageContaining("incorreto");
    }

    @Test
    @DisplayName("Reenvio: espera de 60 s, codigo novo invalida o anterior, no maximo 5 envios por hora")
    void reenvio() {
        String id = "44444444-4444-4444-4444-444444444444";
        var primeiro = service.issue(id, VerificationPurpose.SIGNUP, "r@b.c", Map.of(), false);
        assertThatThrownBy(() -> service.resend(id, VerificationPurpose.SIGNUP))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Aguarde 60 s");

        relogio.avancar(Duration.ofSeconds(60));
        var segundo = service.resend(id, VerificationPurpose.SIGNUP);
        if (!segundo.code().equals(primeiro.code())) {
            assertThatThrownBy(() -> service.verify(id, VerificationPurpose.SIGNUP, primeiro.code()))
                .isInstanceOf(BusinessException.class);
        }
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofSeconds(60));
            service.resend(id, VerificationPurpose.SIGNUP);
        }
        relogio.avancar(Duration.ofSeconds(60));
        assertThatThrownBy(() -> service.resend(id, VerificationPurpose.SIGNUP))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("uma hora");
    }
}
