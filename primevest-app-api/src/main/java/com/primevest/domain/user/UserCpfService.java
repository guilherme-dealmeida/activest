package com.primevest.domain.user;

import com.primevest.domain.auth.verification.SecretHasher;
import com.primevest.domain.integration.credential.CredentialCipher;
import com.primevest.domain.integration.credential.CredentialUnavailableException;
import com.primevest.domain.integration.credential.InvestorDocument;
import com.primevest.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * CPF do usuário (docs/planos/PLANO_CADASTRO_USUARIO.md, 4.2; D12, D18, D20).
 *
 * <p>Cifrado com a mesma cifra das credenciais de integração ({@link CredentialCipher}, AES-256-GCM,
 * {@code PRIMEVEST_CREDENTIALS_KEY}) e o id do usuário como dado associado: o ciphertext copiado para
 * outra linha não decifra. O {@code cpf_hash} (HMAC) dá a unicidade sem decifrar ninguém. A API só
 * devolve o CPF mascarado; o número inteiro é decifrado para uso interno (a conexão com a B3).</p>
 *
 * <p>Perder a chave das credenciais perde também os CPFs (o usuário recadastra), como já vale para
 * as credenciais das integrações.</p>
 */
@Service
public class UserCpfService {

    private final CredentialCipher cipher;
    private final SecretHasher hasher;

    public UserCpfService(CredentialCipher cipher, SecretHasher hasher) {
        this.cipher = cipher;
        this.hasher = hasher;
    }

    /** Só os dígitos, com os verificadores conferidos. Inválido: 422 "Informe um CPF válido.". */
    public String normalize(String raw) {
        try {
            return new InvestorDocument(raw).documentNumber();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    public String hash(String digits) {
        return hasher.hmacHex("cpf:" + digits);
    }

    /** Grava (ou troca) o CPF no usuário, que precisa já ter id. */
    public void apply(User user, String digits) {
        exigirChave();
        CredentialCipher.Sealed sealed = cipher.seal(user.getId(), digits.getBytes(StandardCharsets.UTF_8));
        user.setCpfCiphertext(sealed.ciphertext());
        user.setCpfIv(sealed.iv());
        user.setCpfKeyVersion(sealed.keyVersion());
        user.setCpfHash(hash(digits));
    }

    public void clear(User user) {
        user.setCpfCiphertext(null);
        user.setCpfIv(null);
        user.setCpfKeyVersion(null);
        user.setCpfHash(null);
    }

    /** O CPF em claro (só dígitos), ou vazio se o usuário não tem — ou se a chave para decifrá-lo sumiu. */
    public Optional<String> read(User user) {
        if (!user.hasCpf()) {
            return Optional.empty();
        }
        try {
            byte[] claro = cipher.open(user.getId(),
                new CredentialCipher.Sealed(user.getCpfCiphertext(), user.getCpfIv(), user.getCpfKeyVersion()));
            return Optional.of(new String(claro, StandardCharsets.UTF_8));
        } catch (CredentialUnavailableException e) {
            return Optional.empty();
        }
    }

    /** "***.456.789-**": o meio do número, como se mostra CPF em tela. */
    public static String mask(String digits) {
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    /** CPF do cadastro pendente (Redis): cifrado com o id do pedido como dado associado, nunca em claro. */
    public Map<String, String> sealPending(String requestId, String digits) {
        exigirChave();
        CredentialCipher.Sealed s = cipher.seal(UUID.fromString(requestId), digits.getBytes(StandardCharsets.UTF_8));
        Base64.Encoder b64 = Base64.getEncoder();
        return Map.of("cpfCt", b64.encodeToString(s.ciphertext()), "cpfIv", b64.encodeToString(s.iv()),
            "cpfKv", String.valueOf(s.keyVersion()));
    }

    public Optional<String> openPending(String requestId, Map<String, String> payload) {
        if (payload.get("cpfCt") == null) {
            return Optional.empty();
        }
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] claro = cipher.open(UUID.fromString(requestId), new CredentialCipher.Sealed(
            b64.decode(payload.get("cpfCt")), b64.decode(payload.get("cpfIv")), Short.parseShort(payload.get("cpfKv"))));
        return Optional.of(new String(claro, StandardCharsets.UTF_8));
    }

    private void exigirChave() {
        if (!cipher.isConfigured()) {
            throw new BusinessException("Não é possível guardar o CPF neste ambiente: o servidor está sem a "
                + "chave-mestra (PRIMEVEST_CREDENTIALS_KEY).");
        }
    }
}
