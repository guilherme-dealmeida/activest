package com.primevest.domain.auth.verification;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Contadores de tentativa com janela ({@link VerificationStore}). Excedeu: 429 com a frase dada.
 */
@Component
public class RateLimiter {

    private static final String PREFIX = "rl:";

    private final VerificationStore store;

    public RateLimiter(VerificationStore store) {
        this.store = store;
    }

    /** Conta mais um pedido e recusa se passou de {@code max} na janela. */
    public void hit(String key, int max, Duration window, String message) {
        if (store.increment(PREFIX + key, window) > max) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    /** Recusa se o contador já chegou a {@code max}, sem contar este pedido (login: só falha conta). */
    public void check(String key, int max, String message) {
        if (store.count(PREFIX + key) >= max) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    public void record(String key, Duration window) {
        store.increment(PREFIX + key, window);
    }

    public void reset(String key) {
        store.delete(PREFIX + key);
    }
}
