package com.primevest.domain.integration;

/**
 * Fonte externa de uma conexao (V25, ck_integration_connections_provider).
 *
 * <p>Fonte nova entra por migration, junto com o conector dela
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.2). B3 desde a V40
 * (docs/planos/PLANO_INTEGRACAO_B3.md).</p>
 *
 * <p>O nome em minusculas e o prefixo do {@code externalId} dos movimentos da
 * fonte ('binance:spot:...', 'b3:negociacao:...') — ver {@link #fromExternalId}.</p>
 */
public enum IntegrationProvider {
    BINANCE,
    B3;

    /** A fonte de um externalId pelo prefixo antes do primeiro ':'. */
    public static IntegrationProvider fromExternalId(String externalId) {
        int i = externalId == null ? -1 : externalId.indexOf(':');
        if (i <= 0) {
            throw new IllegalArgumentException("externalId sem prefixo de fonte: " + externalId);
        }
        return valueOf(externalId.substring(0, i).toUpperCase());
    }
}
