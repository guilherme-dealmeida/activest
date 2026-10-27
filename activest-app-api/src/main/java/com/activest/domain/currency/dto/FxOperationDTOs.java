package com.activest.domain.currency.dto;

import com.activest.domain.currency.FxPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Operação de câmbio (docs/planos/PLANO_CADASTRO_MOEDAS.md, 6.3 e D19). */
public final class FxOperationDTOs {

    private FxOperationDTOs() {
    }

    /**
     * @param amountSent     o que saiu, na moeda de origem (reais na remessa)
     * @param amountReceived o que chegou, na moeda de destino (dólares na remessa)
     */
    public record Request(
        @NotNull LocalDate operationDate,
        @NotBlank String fromCurrencyCode,
        @NotBlank String toCurrencyCode,
        @NotNull @Positive BigDecimal amountSent,
        @NotNull @Positive BigDecimal amountReceived,
        @NotNull FxPurpose purpose,
        @PositiveOrZero BigDecimal feeAmount,
        @Size(max = 120) String institution,
        @Size(max = 500) String notes
    ) {
    }

    public record Response(UUID id, LocalDate operationDate, String fromCurrencyCode, String toCurrencyCode,
                           BigDecimal amountSent, BigDecimal amountReceived, BigDecimal effectiveRate,
                           FxPurpose purpose, BigDecimal iofRate, BigDecimal iofAmount, BigDecimal feeAmount,
                           String institution, String iofLegalBasis, String notes) {
    }
}
