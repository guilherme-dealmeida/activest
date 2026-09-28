package com.primevest.domain.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RegisterRequestDTO(@NotBlank String name, @Email String email, @Size(min=8) String password) {}
