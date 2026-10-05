package com.primevest.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
    @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.") String email,
    @NotBlank(message = "Informe a senha.") String password
) {
    @Override
    public String toString() {
        return "LoginRequestDTO[email=" + email + ", password=****]";
    }
}
