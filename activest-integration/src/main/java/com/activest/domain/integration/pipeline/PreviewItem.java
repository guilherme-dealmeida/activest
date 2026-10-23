package com.activest.domain.integration.pipeline;

import com.activest.domain.integration.source.ImportedMovement;

import java.util.UUID;

/**
 * Uma linha da pre-visualizacao.
 *
 * @param transaction preenchida quando {@code outcome} e NEW ou POSSIBLE_DUPLICATE
 * @param reason      por que nao entra (UNMAPPED); nulo nos demais
 * @param duplicateOf o lancamento manual parecido (POSSIBLE_DUPLICATE); nulo nos demais
 */
public record PreviewItem(
    ImportedMovement movement,
    PreviewOutcome outcome,
    ProposedTransaction transaction,
    String reason,
    DuplicateCandidate duplicateOf
) {
    public PreviewItem(ImportedMovement movement, PreviewOutcome outcome, ProposedTransaction transaction,
                       String reason) {
        this(movement, outcome, transaction, reason, null);
    }

    public String externalId() {
        return movement.externalId();
    }

    /** O lancamento manual que pode ser a mesma operacao. */
    public record DuplicateCandidate(UUID transactionId, java.time.LocalDate transactionDate,
                                     java.math.BigDecimal shares, java.math.BigDecimal pricePerShare) {}
}
