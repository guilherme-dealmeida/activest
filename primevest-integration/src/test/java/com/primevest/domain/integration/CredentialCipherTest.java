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
    private static final byte[] SEGREDO = "{\"apiKey\":\"abc\",\"apiSecret\":\"xyz\"}".getBytes(StandardCharsets.UTF_8);

    private final UUID conexao = UUID.randomUUID();

    @Test
    @DisplayName("Cifra e decifra de volta o mesmo conteudo")
    void idaEVolta() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed sealed = cipher.seal(conexao, SEGREDO);

        assertThat(sealed.keyVersion()).isEqualTo((short) 1);
        assertThat(sealed.iv()).hasSize(12);
        assertThat(new String(sealed.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain("xyz");
        assertThat(cipher.open(conexao, sealed)).isEqualTo(SEGREDO);
    }

    @Test
    @DisplayName("IV novo a cada cifragem: o mesmo segredo nunca gera o mesmo ciphertext")
    void ivNovo() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed a = cipher.seal(conexao, SEGREDO);
        CredentialCipher.Sealed b = cipher.seal(conexao, SEGREDO);

        assertThat(a.iv()).isNotEqualTo(b.iv());
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
    }

    @Test
    @DisplayName("AAD: ciphertext de uma conexao nao decifra em outra")
    void presoAConexao() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed sealed = cipher.seal(conexao, SEGREDO);

        assertThatThrownBy(() -> cipher.open(UUID.randomUUID(), sealed))
            .isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("Um byte adulterado derruba a decifragem (tag do GCM)")
    void adulterado() {
        CredentialCipher cipher = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed sealed = cipher.seal(conexao, SEGREDO);
        sealed.ciphertext()[0] ^= 1;

        assertThatThrownBy(() -> cipher.open(conexao, sealed))
            .isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("Rotacao: com a v1 em previous-keys, o antigo decifra e o novo sai na v2")
    void rotacao() {
        CredentialCipher antigo = new CredentialCipher(K1, (short) 1, "");
        CredentialCipher.Sealed gravado = antigo.seal(conexao, SEGREDO);

        CredentialCipher novo = new CredentialCipher(K2, (short) 2, "1:" + K1);
        assertThat(novo.open(conexao, gravado)).isEqualTo(SEGREDO);
        assertThat(novo.seal(conexao, SEGREDO).keyVersion()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("Versao da chave que nao esta configurada: erro claro, sem tentar outra chave")
    void versaoAusente() {
        CredentialCipher.Sealed gravado = new CredentialCipher(K1, (short) 1, "").seal(conexao, SEGREDO);
        CredentialCipher semAV1 = new CredentialCipher(K2, (short) 2, "");

        assertThatThrownBy(() -> semAV1.open(conexao, gravado))
            .isInstanceOf(CredentialUnavailableException.class)
            .hasMessageContaining("versao 1");
    }

    @Test
    @DisplayName("Sem chave a aplicacao sobe, mas cifrar e recusado com mensagem clara")
    void semChave() {
        CredentialCipher cipher = new CredentialCipher("", (short) 1, "");

        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.seal(conexao, SEGREDO))
            .isInstanceOf(CredentialUnavailableException.class)
            .hasMessageContaining("PRIMEVEST_CREDENTIALS_KEY");
    }

    @Test
    @DisplayName("Chave mal formada derruba a subida: tamanho errado, base64 invalido, previous-keys fora do formato")
    void chaveMalFormada() {
        String curta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CredentialCipher(curta, (short) 1, ""))
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
    void credencialMascarada() {
        ApiKeyCredentials c = new ApiKeyCredentials("  chave-publica-a7K2 ", "segredo-muito-secreto");

        assertThat(c.toString()).doesNotContain("segredo").doesNotContain("chave-publica").contains("a7K2");
        assertThat(c.fingerprint()).isEqualTo("a7K2");
        assertThat(c.apiKey()).isEqualTo("chave-publica-a7K2");
        assertThatThrownBy(() -> new ApiKeyCredentials("abc", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
