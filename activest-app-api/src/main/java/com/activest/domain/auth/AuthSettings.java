package com.activest.domain.auth;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Parâmetros do cadastro e dos códigos de verificação (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.2).
 * Valores em {@code activest.auth.*} ({@code application.yml}).
 */
@Getter
@Component
public class AuthSettings {

    /** D16: com {@code false}, nada cria conta nova — nem o cadastro, nem o primeiro login pelo Google. */
    private final boolean signupEnabled;
    private final Duration codeTtl;
    private final int maxAttempts;
    private final Duration resendCooldown;
    private final int maxSendsPerHour;

    public AuthSettings(
            @Value("${activest.auth.signup-enabled:false}") boolean signupEnabled,
            @Value("${activest.auth.code-ttl-minutes:10}") long codeTtlMinutes,
            @Value("${activest.auth.max-attempts:5}") int maxAttempts,
            @Value("${activest.auth.resend-seconds:60}") long resendSeconds,
            @Value("${activest.auth.max-sends-per-hour:5}") int maxSendsPerHour) {
        this.signupEnabled = signupEnabled;
        this.codeTtl = Duration.ofMinutes(codeTtlMinutes);
        this.maxAttempts = maxAttempts;
        this.resendCooldown = Duration.ofSeconds(resendSeconds);
        this.maxSendsPerHour = maxSendsPerHour;
    }
}
