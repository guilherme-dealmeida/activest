package com.primevest.domain.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.transaction.TransactionType;

/**
 * Criação de transação.
 *
 * <p>O fluxo é: o usuário escolhe o TIPO de ativo, e o tipo restringe o
 * autocomplete de ticker. Se o ticker digitado já existe, os campos de ativo
 * abaixo são ignorados — vale o que está cadastrado. Se não existe, o ativo é
 * criado junto com a transação, marcado como custom.</p>
 *
 * <p>Os campos de remuneração só fazem sentido em tipo de renda fixa
 * (asset_types.fixed_income). Para os demais, ficam nulos.</p>
 */
public record TransactionCreateDTO(
    @NotBlank String ticker,
    @NotNull TransactionType type,
    @NotNull @Positive BigDecimal shares,
    @NotNull @Positive BigDecimal pricePerShare,
    BigDecimal brokerageFee,
    @NotNull LocalDate transactionDate,
    Boolean isReservation,

    // --- usados apenas quando o ticker ainda não existe ---
    Long assetTypeId,
    Long assetCategoryId,
    String assetName,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate
) {}
