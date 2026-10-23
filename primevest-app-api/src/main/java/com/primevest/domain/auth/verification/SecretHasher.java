package com.primevest.domain.auth.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 com a chave {@code PRIMEVEST_HASH_KEY}: o código de verificação nunca fica em claro no
 * Redis, e o CPF tem um hash para a unicidade sem precisar decifrar (V41, {@code cpf_hash}).
 *
 * <p>Trocar a chave invalida os códigos em andamento (inofensivo: valem 10 minutos) e os hashes de CPF
 * gravados — a unicidade do CPF deixaria de enxergar os antigos. Por isso a chave é própria, e não a do
 * JWT nem a das credenciais (que tem rotação).</p>
 */
@Slf4j
@Component
public class SecretHasher {

    private final byte[] key;

    public SecretHasher(@Value("${primevest.auth.hash-key:}") String hashKey,
                        @Value("${jwt.secret:}") String jwtSecret) {
        String hmacKey = hashKey;
        if (hmacKey == null || hmacKey.isBlank()) {
            log.warn("PRIMEVEST_HASH_KEY não configurada: usando o JWT_SECRET para os hashes de código e CPF. "
                + "Configure uma chave própria no .env.");
            hmacKey = jwtSecret;
        }
        if (hmacKey == null || hmacKey.length() < 16) {
            throw new IllegalStateException("primevest.auth.hash-key precisa de pelo menos 16 caracteres.");
        }
        this.key = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular o HMAC.", e);
        }
    }

    /** Comparação em tempo constante (não vaza, pelo tempo de resposta, quantos caracteres bateram). */
    public static boolean sameHex(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
