package com.primevest.domain.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.transaction.TransactionType;

public record TransactionCreateDTO(
    @NotBlank String ticker,
    @NotNull TransactionType type,
    @NotNull @Positive BigDecimal shares,
    @NotNull @Positive BigDecimal pricePerShare,
    BigDecimal brokerageFee,
    @NotNull LocalDate transactionDate,
    Boolean isReservation
) {}
