package com.activest.domain.transaction;

import java.util.UUID;

/**
 * O usuário gravou (criou ou editou) uma transação do ativo. Ouvido depois do commit pela liquidação automática: a
 * compra retroativa de um título já vencido nasce liquidada, e a compra editada refaz a venda automática
 * (docs/planos/PLANO_NOTIFICACOES.md, seção 6).
 */
public record PositionWrittenEvent(UUID userId, Long assetId) {
}
