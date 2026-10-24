package com.activest.adm.integration.b3;

/** Falha ao consultar a B3, o FNET ou a CVM: resposta nao-2xx, corpo vazio ou rede. */
public class B3Exception extends RuntimeException {

    public B3Exception(String message) {
        super(message);
    }

    public B3Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
