package com.primevest.domain.integration.source;

/**
 * Resultado da conferencia da chave na fonte (6.4).
 *
 * @param accepted    a fonte aceitou a chave
 * @param readOnly    a chave so le: nao negocia, nao saca, nao transfere
 * @param permissions o que a fonte informou, em JSON, para guardar na credencial
 * @param problem     motivo legivel quando nao aceita ou nao e so leitura
 */
public record CredentialCheck(boolean accepted, boolean readOnly, String permissions, String problem) {

    public boolean usable() {
        return accepted && readOnly;
    }
}
