package com.primevest.integration.coingecko;

/** Resposta nao-2xx da CoinGecko, ou falha de rede. Status 0 = sem resposta. */
public class CoinGeckoException extends RuntimeException {

    private final int status;

    public CoinGeckoException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
