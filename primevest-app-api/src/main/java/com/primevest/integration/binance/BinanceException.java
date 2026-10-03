package com.primevest.integration.binance;

/**
 * Falha ao falar com a Binance. A mensagem nunca carrega a URL assinada, a
 * chave nem o segredo — so o status e o {@code code}/{@code msg} da Binance.
 */
public class BinanceException extends RuntimeException {

    /** Status HTTP; 0 = sem resposta (rede, timeout). */
    private final int status;
    /** Campo {@code code} do corpo de erro da Binance (ex.: -2015), quando veio. */
    private final Integer code;
    /** Segundos do cabecalho Retry-After (429/418). Nulo se nao veio. */
    private final Long retryAfterSeconds;

    public BinanceException(int status, Integer code, String message) {
        this(status, code, message, null);
    }

    public BinanceException(int status, Integer code, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public int status() {
        return status;
    }

    public Integer code() {
        return code;
    }

    /**
     * A Binance recusou a chave: invalida, apagada, sem permissao para o
     * endpoint ou IP fora da lista liberada (-2014, -2015, -1022, 401).
     */
    public boolean isCredentialRejected() {
        return status == 401 || (code != null && (code == -2014 || code == -2015 || code == -1022));
    }

    /** Relogio fora da janela aceita (recvWindow). */
    public boolean isClockSkew() {
        return code != null && code == -1021;
    }

    /** Limite de peso estourado (429) ou IP banido temporariamente (418). */
    public boolean isRateLimited() {
        return status == 429 || status == 418;
    }
}
