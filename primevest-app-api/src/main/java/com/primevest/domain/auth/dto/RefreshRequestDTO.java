package com.primevest.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDTO(@NotBlank(message = "Informe o token de renovação.") String refreshToken) {}
