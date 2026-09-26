package com.primevest.domain.integration.pipeline;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O resultado do pipeline: o que entraria, o que ja existe, o que o usuario
 * excluiu e o que nao e importado. Nada foi gravado ainda — gravar e a F4.
 *
 * @param fiatDeposited soma dos depositos em reais do lote. Nao vira transacao
 *                      (4.6); serve para a conferencia "aportado x comprado"
 */
public record ImportPreview(UUID connectionId, List<PreviewItem> items, BigDecimal fiatDeposited) {

    public ImportPreview {
        items = List.copyOf(items);
    }

    public List<PreviewItem> of(PreviewOutcome outcome) {
        return items.stream().filter(i -> i.outcome() == outcome).toList();
    }

    public long count(PreviewOutcome outcome) {
        return items.stream().filter(i -> i.outcome() == outcome).count();
    }
}
