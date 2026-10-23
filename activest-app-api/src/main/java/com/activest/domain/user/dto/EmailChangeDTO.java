package com.activest.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeDTO(
    @NotBlank(message = "Informe o novo e-mail.") @Email(message = "Informe um e-mail válido.")
    @Size(max = 255, message = "E-mail muito longo.") String newEmail
) {}
