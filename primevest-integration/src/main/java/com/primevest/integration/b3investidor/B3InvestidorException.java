package com.primevest.integration.b3investidor;

/**
 * Falha ao falar com a API da Area do Investidor da B3. A mensagem nunca leva o CPF
 * (a rota sai mascarada), o token nem o segredo do cliente.
 */
public class B3InvestidorException extends RuntimeException {

    /** CPF/CNPJ em formato invalido. */
    public static final String INVALID_DOCUMENT = "422.01";
    /** O investidor nao autorizou o PrimeVest (ou revogou). */
    public static final String NOT_AUTHORIZED = "422.02";
    /** CPF/CNPJ nao localizado na B3. */
    public static final String NOT_FOUND = "422.03";

    /** Status HTTP; 0 = sem resposta (rede, timeout). */
    private final int status;
    /** Codigo de erro da B3 ({@code 422.02}), quando veio. */
    private final String code;
    /** Segundos do cabecalho Retry-After (429). Nulo se nao veio. */
    private final Long retryAfterSeconds;

    public B3InvestidorException(int status, String code, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean is(String b3Code) {
        return b3Code.equals(code);
    }

    /** Vale tentar de novo: limite (429) ou instabilidade (500, 502, 503, 504) ou sem resposta. */
    public boolean isTransient() {
        return status == 0 || status == 429 || status >= 500;
    }
}
