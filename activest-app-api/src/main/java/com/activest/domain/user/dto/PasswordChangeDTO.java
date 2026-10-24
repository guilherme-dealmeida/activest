package com.activest.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alterar a senha (pede a atual) ou definir a primeira (conta só com Google: {@code currentPassword} vazio). */
public record PasswordChangeDTO(
    String currentPassword,
    @NotBlank(message = "Informe a nova senha.") @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.")
    String newPassword
) {
    @Override
    public String toString() {
        return "PasswordChangeDTO[****]";
    }
}
