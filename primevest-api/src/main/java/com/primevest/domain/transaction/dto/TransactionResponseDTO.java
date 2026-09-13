package com.primevest.domain.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.primevest.domain.transaction.TransactionType;

public record TransactionResponseDTO(
    UUID id,
    String ticker,
    String assetName,
    TransactionType type,
    BigDecimal shares,
    BigDecimal pricePerShare,
    BigDecimal totalAmount,
    BigDecimal brokerageFee,
    LocalDate transactionDate,
    LocalDateTime createdAt
) {}
