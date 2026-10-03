package com.primevest.domain.integration.source;

/**
 * Resultado da conferencia da credencial na fonte (6.4).
 *
 * @param accepted              a fonte aceitou a credencial
 * @param readOnly              a credencial so le: nao negocia, nao saca, nao transfere
 * @param permissions           o que a fonte informou, em JSON, para guardar na credencial
 * @param problem               motivo legivel quando nao aceita, nao e so leitura ou falta a autorizacao
 * @param awaitingAuthorization a fonte reconhece o investidor, mas ele ainda nao autorizou o
 *                              PrimeVest (B3: opt-in na Area do Investidor). A conexao fica
 *                              PENDING_AUTHORIZATION (docs/planos/PLANO_INTEGRACAO_B3.md, G8)
 */
public record CredentialCheck(boolean accepted, boolean readOnly, String permissions, String problem,
                              boolean awaitingAuthorization) {

    public CredentialCheck(boolean accepted, boolean readOnly, String permissions, String problem) {
        this(accepted, readOnly, permissions, problem, false);
    }

    /** O investidor ainda nao autorizou: a credencial vale, falta o aceite dele na fonte. */
    public static CredentialCheck pendingAuthorization(String problem) {
        return new CredentialCheck(true, true, null, problem, true);
    }

    public boolean usable() {
        return accepted && readOnly && !awaitingAuthorization;
    }
}
