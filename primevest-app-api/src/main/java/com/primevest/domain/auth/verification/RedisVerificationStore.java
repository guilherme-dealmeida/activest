package com.primevest.domain.auth.verification;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/** {@link VerificationStore} no Redis: o TTL da própria chave apaga o que venceu. */
public class RedisVerificationStore implements VerificationStore {

    private static final String PREFIX = "primevest:auth:";

    private final StringRedisTemplate redis;

    public RedisVerificationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + key));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redis.opsForValue().set(PREFIX + key, value, ttl);
    }

    @Override
    public void delete(String key) {
        redis.delete(PREFIX + key);
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long valor = redis.opsForValue().increment(PREFIX + key);
        if (valor != null && valor == 1L) {
            redis.expire(PREFIX + key, ttl);
        }
        return valor == null ? 0 : valor;
    }

    @Override
    public long count(String key) {
        String valor = redis.opsForValue().get(PREFIX + key);
        return valor == null ? 0 : Long.parseLong(valor);
    }
}
