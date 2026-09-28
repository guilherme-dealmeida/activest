package com.primevest.domain.dividend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.dividend.DividendType;

public record DividendCreateDTO(
    @NotBlank String ticker,
    @NotNull BigDecimal amountPerShare,
    @NotNull LocalDate exDate,
    LocalDate paymentDate,
    @NotNull DividendType type
) {}
