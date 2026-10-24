package com.activest.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CodeConfirmDTO(
    @NotBlank(message = "Pedido não informado.") String requestId,
    @NotBlank(message = "Informe o código.") String code
) {}
