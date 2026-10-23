package com.activest.domain.auth.verification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/**
 * {@code activest.auth.store}: {@code redis} (padrão) ou {@code memory} (perfil de teste, sem Redis).
 */
@Configuration
public class VerificationStoreConfig {

    @Bean
    VerificationStore verificationStore(@Value("${activest.auth.store:redis}") String type,
                                        ObjectProvider<StringRedisTemplate> redis) {
        if ("memory".equalsIgnoreCase(type)) {
            return new InMemoryVerificationStore(Clock.systemUTC());
        }
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("activest.auth.store=redis, mas o Redis não está configurado.");
        }
        return new RedisVerificationStore(template);
    }
}
