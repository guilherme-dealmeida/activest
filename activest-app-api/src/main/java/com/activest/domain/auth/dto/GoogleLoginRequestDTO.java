package com.activest.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequestDTO(@NotBlank(message = "Token do Google não informado.") String credential) {}
