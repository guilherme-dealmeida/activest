package com.primevest.adm.integration.bcb;

/** Fonte do BCB fora do ar, sem copia em disco, ou resposta ilegivel. */
public class BcbException extends RuntimeException {
    public BcbException(String message) {
        super(message);
    }

    public BcbException(String message, Throwable cause) {
        super(message, cause);
    }
}
