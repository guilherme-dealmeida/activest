package com.activest.domain.asset.dto;

public record AssetTypeResponseDTO(
    Long id,
    String name,
    String description,
    String colorHex,
    boolean fixedIncome,
    /** Moeda sugerida e região do tipo (docs/planos/PLANO_CADASTRO_MOEDAS.md, D2/D4). */
    String defaultCurrencyCode,
    String region
) {}
