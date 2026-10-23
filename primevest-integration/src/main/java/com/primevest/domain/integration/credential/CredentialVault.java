package com.primevest.domain.integration.credential;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.integration.IntegrationConnection;
import com.primevest.domain.integration.IntegrationCredential;
import com.primevest.domain.integration.IntegrationCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * O unico lugar que guarda, le e apaga credenciais de integracao.
 *
 * <p>Fora daqui o segredo so existe em memoria, dentro de um
 * {@link SourceCredentials}, pelo tempo de uma chamada a fonte. Nenhum DTO
 * carrega a credencial de volta: a API expoe so o {@code fingerprint}
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.4 — "segredo nunca volta").</p>
 *
 * <p>Formato do JSON cifrado: chave de API como sempre foi ({@code apiKey}, {@code apiSecret} —
 * as credenciais ja gravadas continuam legiveis); documento do investidor com
 * {@code documentNumber} (docs/planos/PLANO_INTEGRACAO_B3.md, G2).</p>
 */
@Service
public class CredentialVault {

    private final CredentialCipher cipher;
    private final IntegrationCredentialRepository repository;
    private final ObjectMapper objectMapper;

    public CredentialVault(CredentialCipher cipher, IntegrationCredentialRepository repository,
                           ObjectMapper objectMapper) {
        this.cipher = cipher;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Grava (ou troca) a credencial da conexao. A conexao precisa estar salva:
     * o id dela entra na cifragem.
     */
    @Transactional
    public IntegrationCredential store(IntegrationConnection connection, SourceCredentials credentials) {
        IntegrationCredential entity = repository.findById(connection.getId()).orElseGet(() -> {
            IntegrationCredential newValue = new IntegrationCredential();
            newValue.setConnection(connection);
            return newValue;
        });
        byte[] plaintext = toJson(credentials);
        try {
            CredentialCipher.Sealed sealed = cipher.seal(connection.getId(), plaintext);
            entity.setCiphertext(sealed.ciphertext());
            entity.setIv(sealed.iv());
            entity.setKeyVersion(sealed.keyVersion());
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
        entity.setFingerprint(credentials.fingerprint());
        // Credencial nova ainda nao foi conferida na fonte.
        entity.setValidatedAt(null);
        entity.setPermissions(null);
        return repository.save(entity);
    }

    /** Decifra a credencial para uma chamada a fonte. */
    @Transactional(readOnly = true)
    public SourceCredentials read(UUID connectionId) {
        IntegrationCredential entity = repository.findById(connectionId)
            .orElseThrow(() -> new CredentialUnavailableException("A conexao nao tem credencial gravada."));
        byte[] plaintext = cipher.open(connectionId,
            new CredentialCipher.Sealed(entity.getCiphertext(), entity.getIv(), entity.getKeyVersion()));
        try {
            Map<?, ?> json = objectMapper.readValue(plaintext, Map.class);
            if (json.containsKey("documentNumber")) {
                return new InvestorDocument((String) json.get("documentNumber"));
            }
            return new ApiKeyCredentials((String) json.get("apiKey"), (String) json.get("apiSecret"));
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new CredentialUnavailableException("A credencial decifrada nao tem o formato esperado.");
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /**
     * Recifra com a chave atual quando a gravada e de versao anterior. Devolve
     * {@code true} se recifrou. E o passo da rotacao de chave.
     */
    @Transactional
    public boolean reseal(IntegrationConnection connection) {
        IntegrationCredential entity = repository.findById(connection.getId())
            .orElseThrow(() -> new CredentialUnavailableException("A conexao nao tem credencial gravada."));
        if (entity.getKeyVersion() == cipher.currentVersion()) {
            return false;
        }
        String permissions = entity.getPermissions();
        var validatedAt = entity.getValidatedAt();
        store(connection, read(connection.getId()));
        // Recifrar nao muda a credencial: a ultima checagem continua valendo.
        entity.setPermissions(permissions);
        entity.setValidatedAt(validatedAt);
        return true;
    }

    /** Apaga a credencial (revogacao). Nao ha soft delete de segredo. */
    @Transactional
    public void erase(UUID connectionId) {
        repository.findById(connectionId).ifPresent(repository::delete);
    }

    private byte[] toJson(SourceCredentials credentials) {
        Map<String, String> json = switch (credentials) {
            case ApiKeyCredentials k -> Map.of("apiKey", k.apiKey(), "apiSecret", k.apiSecret());
            case InvestorDocument d -> Map.of("documentNumber", d.documentNumber());
        };
        try {
            return objectMapper.writeValueAsBytes(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a credencial.", e);
        }
    }
}
