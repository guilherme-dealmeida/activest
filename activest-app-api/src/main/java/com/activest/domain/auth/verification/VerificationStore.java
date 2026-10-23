package com.activest.domain.auth.verification;

import java.time.Duration;
import java.util.Optional;

/**
 * Onde ficam os códigos, os cadastros pendentes e os contadores de tentativa: tudo com prazo de
 * validade, nada em tabela (D3). Redis na aplicação; memória nos testes, que rodam sem Redis.
 */
public interface VerificationStore {

    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);

    void delete(String key);

    /** Soma 1 e devolve o novo valor; o prazo começa a contar no primeiro incremento. */
    long increment(String key, Duration ttl);

    /** Valor atual do contador (0 se não existe ou venceu). */
    long count(String key);
}
