package com.primevest.domain.integration.dto;

import com.primevest.domain.integration.RunItemResolution;
import com.primevest.domain.integration.RunStatus;
import com.primevest.domain.integration.pipeline.PreviewOutcome;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Uma execucao da importacao (PLANO_CRIPTO_BINANCE.md, F4): a pre-visualizacao
 * guardada e, depois de confirmada, o que aconteceu com cada item.
 *
 * @param runId         o que se confirma ({@code POST .../runs/{runId}/confirm})
 * @param status        AWAITING_CONFIRMATION ate confirmar; SUCCEEDED ou CANCELLED depois
 * @param fiatDeposited soma dos depositos em reais — conferencia "aportado x comprado"
 * @param error         por que a busca falhou (FAILED) ou a ultima tentativa de confirmar (a execucao segue aguardando)
 * @param divergences   regra E3: ativos em que a posicao no PrimeVest difere do saldo da corretora, depois de
 *                      confirmada. Posicao de outras carteiras/corretoras do mesmo ativo tambem aparece aqui
 */
public record ImportPreviewDTO(
    UUID connectionId,
    String connectionLabel,
    UUID runId,
    java.time.LocalDateTime createdAt,
    java.time.LocalDateTime finishedAt,
    RunStatus status,
    com.primevest.domain.integration.RunTrigger trigger,
    long newCount,
    long existingCount,
    long excludedCount,
    long unmappedCount,
    long duplicateCount,
    BigDecimal fiatDeposited,
    String error,
    List<Divergence> divergences,
    List<Item> items
) {
    /** Saldo na corretora x posicao no PrimeVest (difference = corretora - PrimeVest). */
    public record Divergence(String asset, BigDecimal primevest, BigDecimal exchange, BigDecimal difference) {}

    /** O movimento como veio da fonte e, se entra, a transacao que vira. */
    public record Item(
        String externalId,
        MovementKind kind,
        PreviewOutcome outcome,
        String reason,
        Instant executedAt,
        String asset,
        MovementSide side,
        BigDecimal grossQuantity,
        String quoteAsset,
        BigDecimal quoteAmount,
        BigDecimal feeAmount,
        String feeAsset,
        Proposed transaction,
        /** O lancamento manual parecido (POSSIBLE_DUPLICATE): decida REPLACE, KEEP_BOTH ou SKIP. */
        Duplicate duplicateOf,
        /** O que a confirmacao fez; nulo antes dela. */
        RunItemResolution resolution,
        /** A transacao criada pela confirmacao. */
        UUID transactionId
    ) {}

    public record Proposed(
        String ticker,
        TransactionType type,
        BigDecimal shares,
        BigDecimal pricePerShare,
        BigDecimal totalAmount,
        BigDecimal brokerageFee,
        LocalDate transactionDate,
        BigDecimal roundingDifference
    ) {}

    public record Duplicate(
        UUID transactionId,
        LocalDate transactionDate,
        BigDecimal shares,
        BigDecimal pricePerShare
    ) {}
}
