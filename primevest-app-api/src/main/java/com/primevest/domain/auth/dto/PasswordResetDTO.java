package com.primevest.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetDTO(
    @NotBlank(message = "Pedido não informado.") String requestId,
    @NotBlank(message = "Informe o código.") String code,
    @NotBlank(message = "Informe a nova senha.") @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres.")
    String newPassword
) {
    @Override
    public String toString() {
        return "PasswordResetDTO[requestId=" + requestId + ", code=****, newPassword=****]";
    }
}
