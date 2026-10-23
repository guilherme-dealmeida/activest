package com.primevest.domain.integration;

import com.primevest.domain.integration.credential.ApiKeyCredentials;
import com.primevest.domain.integration.credential.CredentialCipher;
import com.primevest.domain.integration.credential.CredentialUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private static final String K1 = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String K2 = Base64.getEncoder().encodeToString("chave-dois-com-exatos-32-bytes!!".getBytes(StandardCharsets.US_ASCII));
    private static final byte[] SECRET = "{\"apiKey\":\"abc\",\"apiSecret\":\"xyz\"}".getBytes(StandardCharsets.UTF_8);

    private final UUID connection = UUID.randomUUID();

    @Test
    @DisplayName("Cifra e decifra de volta o mesmo conteudo")
    void roundTrip() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed sealed = cipher.seal(connection, SECRET);

        assertThat(sealed.keyVersion()).isEqualTo((short) 1);
        assertThat(sealed.iv()).hasSize(12);
        assertThat(new String(sealed.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain("xyz");
        assertThat(cipher.open(connection, sealed)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("IV novo a cada cifragem: o mesmo segredo nunca gera o mesmo ciphertext")
    void newIv() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed a = cipher.seal(connection, SECRET);
        CredentialCipher.Sealed b = cipher.seal(connection, SECRET);

        assertThat(a.iv()).isNotEqualTo(b.iv());
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
    }

    @Test
    @DisplayName("AAD: ciphertext de uma conexao nao decifra em outra")
    void boundToConnection() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed sealed = cipher.seal(connection, SECRET);

        assertThatThrownBy(() -> cipher.open(UUID.randomUUID(), sealed))
            .isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("Um byte adulterado derruba a decifragem (tag do GCM)")
    void tampered() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed sealed = cipher.seal(connection, SECRET);
        sealed.ciphertext()[0] ^= 1;

        assertThatThrownBy(() -> cipher.open(connection, sealed))
            .isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("Rotacao: com a v1 em previous-keys, o antigo decifra e o novo sai na v2")
    void rotation() {
        CredentialCipher previous = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed stored = previous.seal(connection, SECRET);

        CredentialCipher created = new CredentialCipher(K2, (short) 2, "1:" + K1);
        assertThat(created.open(connection, stored)).isEqualTo(SECRET);
        assertThat(created.seal(connection, SECRET).keyVersion()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("Versao da chave que nao esta configurada: erro claro, sem tentar outra chave")
    void missingVersion() {
        CredentialCipher.Sealed stored = new CredentialCipher(K1, (short) 1, "").seal(connection, SECRET);
        CredentialCipher withoutV1 = new CredentialCipher(K2, (short) 2, "");

        assertThatThrownBy(() -> withoutV1.open(connection, stored))
            .isInstanceOf(CredentialUnavailableException.class)
            .hasMessageContaining("versao 1");
    }

    @Test
    @DisplayName("Sem chave a aplicacao sobe, mas cifrar e recusado com mensagem clara")
    void withoutKey() {
        CredentialCipher cipher = new CredentialCipher("", (short) 1, "");

        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.seal(connection, SECRET))
            .isInstanceOf(CredentialUnavailableException.class)
            .hasMessageContaining("PRIMEVEST_CREDENTIALS_KEY");
    }

    @Test
    @DisplayName("Chave mal formada derruba a subida: tamanho errado, base64 invalido, previous-keys fora do formato")
    void malformedKey() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CredentialCipher(tooShort, (short) 1, ""))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new CredentialCipher("nao-e-base64!!", (short) 1, ""))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
        assertThatThrownBy(() -> new CredentialCipher(K1, (short) 1, K2))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("versao:base64");
        assertThatThrownBy(() -> new CredentialCipher(K1, (short) 1, "1:" + K2))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("valores diferentes");
    }

    @Test
    @DisplayName("ApiKeyCredentials: toString nunca mostra o segredo; fingerprint sao os 4 ultimos da chave")
    void maskedCredential() {
        ApiKeyCredentials c = new ApiKeyCredentials("  chave-publica-a7K2 ", "segredo-muito-secreto");

        assertThat(c.toString()).doesNotContain("segredo").doesNotContain("chave-publica").contains("a7K2");
        assertThat(c.fingerprint()).isEqualTo("a7K2");
        assertThat(c.apiKey()).isEqualTo("chave-publica-a7K2");
        assertThatThrownBy(() -> new ApiKeyCredentials("abc", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
