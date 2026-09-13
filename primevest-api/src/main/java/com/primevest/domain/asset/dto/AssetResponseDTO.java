package com.primevest.domain.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssetResponseDTO(
    Long id,
    String ticker,
    String name,
    String typeName,
    String categoryName,
    String sector,
    BigDecimal currentPrice,
    LocalDateTime priceUpdatedAt,
    LocalDate maturityDate,
    String indexer,
    BigDecimal indexerRate,
    boolean active
) {}
