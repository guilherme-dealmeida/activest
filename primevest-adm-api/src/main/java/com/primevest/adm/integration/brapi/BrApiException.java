package com.primevest.adm.integration.brapi;

/**
 * Resposta nao-2xx da BRAPI, ou falha de rede.
 *
 * <p>Antes o cliente engolia tudo num {@code catch (Exception)} e devolvia
 * lista vazia: nenhum preco era atualizado e o log nao dizia por que
 * (PLANO_BRAPI.md, D2). Agora o erro sobe com o que a BRAPI respondeu, e quem
 * chama decide: 404 pula o ativo, 401/403 interrompe a rodada.</p>
 *
 * <p>Codigos que a BRAPI usa: 400 parametro fora do plano
 * ({@code INVALID_RANGE}, {@code QUOTES_PER_REQUEST_EXCEEDED}), 401 token,
 * 403 recurso de outro plano ({@code FEATURE_NOT_AVAILABLE}), 404
 * {@code NOT_FOUND}, 429 cota ou simultaneidade (com {@code Retry-After}).</p>
 */
public class BrApiException extends RuntimeException {

    /** Status HTTP; 0 = sem resposta (rede, timeout). */
    private final int status;
    /** Campo {@code code} do corpo de erro da BRAPI, quando veio. */
    private final String code;
    /** Segundos do cabecalho Retry-After (429). Nulo se nao veio. */
    private final Long retryAfterSeconds;

    public BrApiException(int status, String code, String message, Long retryAfterSeconds) {
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

    public boolean isNotFound() {
        return status == 404;
    }

    /** Token invalido ou recurso fora do plano: repetir nao adianta, a rodada para. */
    public boolean isAuthOrPlan() {
        return status == 401 || status == 403;
    }

    public boolean isRateLimited() {
        return status == 429;
    }

    @Override
    public String toString() {
        return "BrApiException{status=" + status + ", code=" + code + ", message=" + getMessage() + "}";
    }
}
