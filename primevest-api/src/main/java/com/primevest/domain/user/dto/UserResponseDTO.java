package com.primevest.domain.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDTO(
    UUID id,
    String name,
    String email,
    String avatarUrl,
    LocalDateTime createdAt
) {}
