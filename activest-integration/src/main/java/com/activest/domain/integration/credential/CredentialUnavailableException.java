package com.activest.domain.integration.credential;

/**
 * A credencial nao pode ser usada: chave-mestra ausente, versao nao configurada
 * ou ciphertext que nao decifra. A mensagem nunca carrega o segredo.
 */
public class CredentialUnavailableException extends RuntimeException {
    public CredentialUnavailableException(String message) {
        super(message);
    }
}
