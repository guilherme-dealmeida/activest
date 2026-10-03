package com.primevest.domain.dividend;

import java.math.BigDecimal;
import java.util.UUID;

/** Soma dos proventos de uma posição. Resultado de DividendRepository.sumTotalAmountByPosition. */
public record PositionDividendTotal(UUID portfolioAssetId, BigDecimal total) {}
