package com.activest.domain.integration.dto;

import com.activest.domain.integration.source.MovementSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Operacao importada que o usuario excluiu (regra E2). Restaurar = POST .../restore. */
public record ExclusionDTO(
    UUID id,
    String externalId,
    String ticker,
    LocalDateTime excludedAt,
    Instant executedAt,
    MovementSide side,
    BigDecimal grossQuantity,
    BigDecimal quoteAmount
) {}
