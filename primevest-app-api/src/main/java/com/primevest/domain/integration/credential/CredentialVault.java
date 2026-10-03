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
 * {@link ApiKeyCredentials}, pelo tempo de uma chamada a corretora. Nenhum DTO
 * carrega a credencial de volta: a API expoe so o {@code fingerprint}
 * (PLANO_CRIPTO_BINANCE.md, 6.4 — "segredo nunca volta").</p>
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
    public IntegrationCredential store(IntegrationConnection connection, ApiKeyCredentials credentials) {
        IntegrationCredential entity = repository.findById(connection.getId()).orElseGet(() -> {
            IntegrationCredential nova = new IntegrationCredential();
            nova.setConnection(connection);
            return nova;
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
    public ApiKeyCredentials read(UUID connectionId) {
        IntegrationCredential entity = repository.findById(connectionId)
            .orElseThrow(() -> new CredentialUnavailableException("A conexao nao tem credencial gravada."));
        byte[] plaintext = cipher.open(connectionId,
            new CredentialCipher.Sealed(entity.getCiphertext(), entity.getIv(), entity.getKeyVersion()));
        try {
            Map<?, ?> json = objectMapper.readValue(plaintext, Map.class);
            return new ApiKeyCredentials((String) json.get("apiKey"), (String) json.get("apiSecret"));
        } catch (java.io.IOException e) {
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

    private byte[] toJson(ApiKeyCredentials credentials) {
        try {
            return objectMapper.writeValueAsBytes(
                Map.of("apiKey", credentials.apiKey(), "apiSecret", credentials.apiSecret()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a credencial.", e);
        }
    }
}
