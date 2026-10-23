package com.primevest.domain.auth;

import com.primevest.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthHelpersTest {

    @Test
    @DisplayName("E-mail normalizado (G6) e mascarado")
    void emails() {
        assertThat(Emails.normalize("  Guilherme@Gmail.COM ")).isEqualTo("guilherme@gmail.com");
        assertThat(Emails.mask("guilherme@gmail.com")).isEqualTo("gu*******@gmail.com");
        assertThat(Emails.mask("ab@x.io")).isEqualTo("a***@x.io");
    }

    @Test
    @DisplayName("Celular em E.164: DDD brasileiro ganha +55; internacional com +; invalido recusado")
    void phones() {
        assertThat(Phones.normalize("(41) 99876-5432")).isEqualTo("+5541998765432");
        assertThat(Phones.normalize("4133334444")).isEqualTo("+554133334444");
        assertThat(Phones.normalize("+55 41 99876-5432")).isEqualTo("+5541998765432");
        assertThat(Phones.normalize("+1 415 555 0100")).isEqualTo("+14155550100");
        assertThat(Phones.normalize("  ")).isNull();
        assertThatThrownBy(() -> Phones.normalize("12345")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Token de acesso e de renovacao nao se confundem (U1)")
    void tokenTypes() {
        JwtTokenProvider p = new JwtTokenProvider("chave-de-teste-com-pelo-menos-256-bits-para-hmac-sha256-primevest",
            60_000, 120_000);
        UUID id = UUID.randomUUID();
        String access = p.generateToken(id, "a@b.c");
        String refresh = p.generateRefreshToken(id);

        assertThat(p.parseAccess(access)).get().extracting(JwtTokenProvider.TokenClaims::userId).isEqualTo(id);
        assertThat(p.parseAccess(refresh)).isEmpty();
        assertThat(p.parseRefresh(refresh)).isPresent();
        assertThat(p.parseRefresh(access)).isEmpty();
        assertThat(p.parseAccess("lixo")).isEmpty();
    }
}
