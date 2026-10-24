package com.activest.domain.auth.dto;

/**
 * Resposta de todo pedido que manda código por e-mail. Igual para e-mail que existe e que não existe
 * (D10): só o dono da caixa de entrada sabe se o código chegou.
 */
public record CodeSentDTO(String requestId, String maskedEmail, long resendInSeconds, long expiresInSeconds) {}
