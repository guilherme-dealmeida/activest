package com.primevest.domain.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;

public record AssetResponseDTO(
    Long id,
    String ticker,
    String name,
    Long typeId,
    String typeName,
    Long categoryId,
    String categoryName,
    String sector,
    BigDecimal currentPrice,
    LocalDateTime priceUpdatedAt,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate,
    boolean active
) {}
