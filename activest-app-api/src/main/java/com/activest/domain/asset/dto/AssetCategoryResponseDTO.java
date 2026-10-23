package com.activest.domain.asset.dto;

public record AssetCategoryResponseDTO(
    Long id,
    String name,
    String description,
    Long assetTypeId,
    String assetTypeName
) {}
