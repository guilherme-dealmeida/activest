package com.activest.domain.notification;

import com.activest.domain.catalog.CatalogChange;

/**
 * Algo que reage ao "catálogo mudou" depois que a carteira recalculou (docs/planos/PLANO_NOTIFICACOES.md): as
 * mensagens que dependem do catálogo. Cada uma decide pela origem; uma falha não impede as outras.
 */
public interface CatalogReaction {
    void after(CatalogChange change);
}
