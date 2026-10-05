package com.primevest.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Reenvio de código: só o id do pedido. */
public record CodeRequestDTO(@NotBlank(message = "Pedido não informado.") String requestId) {}
