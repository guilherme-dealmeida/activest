package com.primevest.domain.auth.verification;

import java.util.Map;

/**
 * Um código emitido e o que ele libera, como fica guardado (JSON) no {@link VerificationStore}.
 *
 * @param decoy   pedido de fachada (D10): o e-mail já tem conta (cadastro) ou não tem (esqueci a senha).
 *                Responde igual a um pedido de verdade, mas nenhum código o confirma
 * @param payload o que o código libera (cadastro pendente, usuário, e-mail novo...)
 */
public record PendingCode(
    String requestId,
    VerificationPurpose purpose,
    String email,
    String codeHash,
    int attempts,
    long lastSentEpochSecond,
    boolean decoy,
    Map<String, String> payload
) {
    public PendingCode {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public String get(String key) {
        return payload.get(key);
    }

    @Override
    public String toString() {
        // o payload pode ter hash de senha e CPF cifrado: fica fora de qualquer log
        return "PendingCode[" + requestId + ", " + purpose + ", attempts=" + attempts + ", decoy=" + decoy + "]";
    }
}
