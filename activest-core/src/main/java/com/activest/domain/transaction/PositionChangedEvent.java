package com.activest.domain.transaction;

/**
 * Uma posicao de renda variavel mudou (transacao criada, editada ou excluida).
 * Publicado pelo TransactionService; quem ouve, ouve depois do commit.
 */
public record PositionChangedEvent(Long assetId) {
}
