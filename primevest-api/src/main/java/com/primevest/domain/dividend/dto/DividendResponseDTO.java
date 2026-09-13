package com.primevest.domain.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.dividend.DividendType;

public record DividendResponseDTO(
    Long id,
    String ticker,
    String assetName,
    BigDecimal amountPerShare,
    BigDecimal totalAmount,
    LocalDate exDate,
    LocalDate paymentDate,
    DividendType type
) {}
