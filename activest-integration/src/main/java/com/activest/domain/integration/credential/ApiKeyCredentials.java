package com.activest.domain.integration.credential;

/**
 * Credencial de corretora no formato chave + segredo (Binance e a maioria das
 * corretoras cripto).
 *
 * <p>{@code toString} mascarado: um record imprimiria o segredo em claro em
 * qualquer log ou mensagem de excecao que o recebesse.</p>
 */
public record ApiKeyCredentials(String apiKey, String apiSecret) implements SourceCredentials {

    public ApiKeyCredentials {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Informe a chave de API.");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("Informe o segredo da chave de API.");
        }
        apiKey = apiKey.strip();
        apiSecret = apiSecret.strip();
    }

    /** Os ultimos 4 caracteres da chave: o unico pedaco que a tela mostra. */
    @Override
    public String fingerprint() {
        return apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4);
    }

    @Override
    public String toString() {
        return "ApiKeyCredentials[apiKey=****" + fingerprint() + ", apiSecret=****]";
    }
}
