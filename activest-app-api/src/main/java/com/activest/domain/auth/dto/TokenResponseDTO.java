package com.activest.domain.auth.dto;
public record TokenResponseDTO(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
