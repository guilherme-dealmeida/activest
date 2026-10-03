package com.primevest.adm.integration.cvm;

/** Falha ao obter ou ler um arquivo dos dados abertos da CVM. */
public class CvmException extends RuntimeException {

    public CvmException(String message) {
        super(message);
    }

    public CvmException(String message, Throwable cause) {
        super(message, cause);
    }
}
