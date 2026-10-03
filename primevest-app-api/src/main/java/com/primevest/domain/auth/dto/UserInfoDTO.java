package com.primevest.domain.auth.dto;
import java.util.UUID;
public record UserInfoDTO(UUID id, String name, String email, String avatarUrl) {}
