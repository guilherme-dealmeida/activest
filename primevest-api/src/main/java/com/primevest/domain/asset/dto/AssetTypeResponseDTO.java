package com.primevest.domain.asset.dto;

public record AssetTypeResponseDTO(
    Long id,
    String name,
    String description,
    String colorHex
) {}
