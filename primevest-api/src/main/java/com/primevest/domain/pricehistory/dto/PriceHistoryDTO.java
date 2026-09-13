package com.primevest.domain.pricehistory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceHistoryDTO(
    LocalDate date,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    Long volume
) {}
