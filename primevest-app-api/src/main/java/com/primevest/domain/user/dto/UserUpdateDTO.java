package com.primevest.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserUpdateDTO(
    @NotBlank String name,
    String avatarUrl
) {}
