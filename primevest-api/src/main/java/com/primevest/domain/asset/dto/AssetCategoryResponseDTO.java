package com.primevest.domain.asset.dto;

public record AssetCategoryResponseDTO(
    Long id,
    String name,
    String description,
    String segment,
    Long assetTypeId,
    String assetTypeName
) {}
