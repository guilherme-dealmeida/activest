package com.primevest.adm.integration.indices;

/** A fonte de um indice (SGS ou B3) recusou ou nao respondeu, e nao ha copia em disco. */
public class IndexSourceException extends RuntimeException {
    public IndexSourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public IndexSourceException(String message) {
        super(message);
    }
}
