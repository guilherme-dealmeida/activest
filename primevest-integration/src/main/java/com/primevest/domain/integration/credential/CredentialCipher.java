package com.primevest.domain.integration.credential;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cifragem das credenciais de integracao: AES-256-GCM, com a chave-mestra
 * fora do banco (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.3).
 *
 * <p>Tres garantias, cada uma com um porque:</p>
 * <ul>
 *   <li><b>IV novo a cada cifragem</b> (12 bytes do {@link SecureRandom}).
 *   Repetir IV com a mesma chave no GCM quebra a confidencialidade e a
 *   autenticidade de uma vez.</li>
 *   <li><b>O id da conexao e o dado associado (AAD).</b> O ciphertext fica
 *   preso a linha: copiado para outra conexao, nao decifra.</li>
 *   <li><b>Versao da chave gravada junto.</b> Para rotacionar, a chave nova
 *   vira a atual e a antiga passa para {@code previous-keys}: o que foi
 *   gravado com ela continua legivel ate ser recifrado.</li>
 * </ul>
 *
 * <p>Sem chave configurada a aplicacao sobe normalmente — so as integracoes
 * ficam indisponiveis, com mensagem clara. Chave mal formada derruba a subida:
 * descobrir isso na primeira conexao de um usuario seria pior.</p>
 *
 * <p>Configuracao ({@code application.yml}, vinda do {@code .env}):</p>
 * <pre>
 * primevest.integrations.credentials.key            PRIMEVEST_CREDENTIALS_KEY (base64, 32 bytes)
 * primevest.integrations.credentials.key-version    PRIMEVEST_CREDENTIALS_KEY_VERSION (padrao 1)
 * primevest.integrations.credentials.previous-keys  "1:&lt;base64&gt;,2:&lt;base64&gt;" (so leitura)
 * </pre>
 */
@Slf4j
@Component
public class CredentialCipher {

    static final int KEY_BYTES = 32;
    static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecureRandom random = new SecureRandom();
    private final Map<Short, SecretKey> keys;
    private final Short currentVersion;

    /** Resultado da cifragem: o que vai para integration_credentials. */
    public record Sealed(byte[] ciphertext, byte[] iv, short keyVersion) {
        @Override
        public String toString() {
            return "Sealed[keyVersion=" + keyVersion + ", " + ciphertext.length + " bytes]";
        }
    }

    public CredentialCipher(
            @Value("${primevest.integrations.credentials.key:}") String currentKey,
            @Value("${primevest.integrations.credentials.key-version:1}") short currentVersion,
            @Value("${primevest.integrations.credentials.previous-keys:}") String previousKeys) {

        Map<Short, SecretKey> all = new HashMap<>(parsePrevious(previousKeys));
        if (currentKey == null || currentKey.isBlank()) {
            this.currentVersion = null;
            log.warn("PRIMEVEST_CREDENTIALS_KEY nao configurada: conexoes com corretoras ficam indisponiveis.");
        } else {
            if (currentVersion <= 0) {
                throw new IllegalStateException("primevest.integrations.credentials.key-version deve ser maior que zero.");
            }
            SecretKey key = decodeKey(currentKey, "PRIMEVEST_CREDENTIALS_KEY");
            SecretKey anterior = all.put(currentVersion, key);
            if (anterior != null && !java.util.Arrays.equals(anterior.getEncoded(), key.getEncoded())) {
                throw new IllegalStateException("A versao " + currentVersion
                    + " aparece como chave atual e como chave anterior, com valores diferentes.");
            }
            this.currentVersion = currentVersion;
        }
        this.keys = Collections.unmodifiableMap(all);
    }

    /** Ha chave atual para cifrar. Sem ela, nenhuma conexao nova pode ser criada. */
    public boolean isConfigured() {
        return currentVersion != null;
    }

    /** A versao com que {@link #seal} cifra hoje. */
    public short currentVersion() {
        requireConfigured();
        return currentVersion;
    }

    public Sealed seal(UUID connectionId, byte[] plaintext) {
        requireConfigured();
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(connectionId));
            return new Sealed(cipher.doFinal(plaintext), iv, currentVersion);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar a credencial.", e);
        }
    }

    public byte[] open(UUID connectionId, Sealed sealed) {
        SecretKey key = keys.get(sealed.keyVersion());
        if (key == null) {
            throw new CredentialUnavailableException("A credencial foi cifrada com a chave versao "
                + sealed.keyVersion() + ", que nao esta configurada.");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed.iv()));
            cipher.updateAAD(aad(connectionId));
            return cipher.doFinal(sealed.ciphertext());
        } catch (AEADBadTagException e) {
            // Chave errada, dado adulterado ou ciphertext de outra conexao: o GCM
            // nao distingue os tres, e nenhum deles deve ser tentado de novo.
            throw new CredentialUnavailableException("A credencial nao pode ser decifrada (chave ou dado divergente).");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar a credencial.", e);
        }
    }

    private void requireConfigured() {
        if (currentVersion == null) {
            throw new CredentialUnavailableException(
                "Integracoes indisponiveis: PRIMEVEST_CREDENTIALS_KEY nao esta configurada no servidor.");
        }
    }

    private static byte[] aad(UUID connectionId) {
        if (connectionId == null) {
            throw new IllegalArgumentException("A conexao precisa estar salva antes de cifrar a credencial.");
        }
        return connectionId.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static Map<Short, SecretKey> parsePrevious(String value) {
        Map<Short, SecretKey> map = new HashMap<>();
        if (value == null || value.isBlank()) {
            return map;
        }
        for (String entry : value.split(",")) {
            String[] parts = entry.strip().split(":", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("previous-keys deve ter o formato 'versao:base64', separado por virgula.");
            }
            short version;
            try {
                version = Short.parseShort(parts[0].strip());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Versao invalida em previous-keys: '" + parts[0].strip() + "'.");
            }
            if (version <= 0 || map.put(version, decodeKey(parts[1].strip(), "previous-keys v" + version)) != null) {
                throw new IllegalStateException("Versao repetida ou invalida em previous-keys: " + version + ".");
            }
        }
        return map;
    }

    private static SecretKey decodeKey(String base64, String name) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " nao e base64 valido.");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(name + " deve ter 32 bytes (256 bits); tem " + bytes.length + ".");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
