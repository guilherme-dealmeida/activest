package com.activest.domain.asset.dto;

public record AssetTypeResponseDTO(
    Long id,
    String name,
    String description,
    String colorHex,
    boolean fixedIncome
) {}
