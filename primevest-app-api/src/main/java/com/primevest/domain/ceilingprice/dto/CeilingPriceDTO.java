package com.primevest.domain.ceilingprice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CeilingPriceDTO(
    UUID id,
    String ticker,
    String assetName,
    String assetTypeName,
    BigDecimal maxPrice,
    BigDecimal currentPrice,
    BigDecimal diffPercentage,
    boolean alertEnabled,
    boolean belowCeiling,
    /** Emissor do catalogo (assets.issuer_id), para o link da tela de emissoras. Nulo sem emissor. */
    Long issuerId
) {}
