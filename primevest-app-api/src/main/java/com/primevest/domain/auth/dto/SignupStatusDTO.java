package com.primevest.domain.auth.dto;

/**
 * {@code GET /auth/signup/enabled}: a tela de login decide se mostra "Criar conta" (D16) e qual versão
 * dos termos o cadastro aceita.
 */
public record SignupStatusDTO(boolean enabled, String termsVersion) {}
