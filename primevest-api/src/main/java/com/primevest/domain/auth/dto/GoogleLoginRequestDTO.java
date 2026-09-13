package com.primevest.domain.auth.dto;
import jakarta.validation.constraints.NotBlank;
public record GoogleLoginRequestDTO(@NotBlank String credential) {}
