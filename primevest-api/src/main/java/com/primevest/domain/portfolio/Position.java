package com.primevest.domain.portfolio;

import java.math.BigDecimal;

/**
 * Posicao apurada a partir das transacoes. Nunca e gravada.
 *
 * <p>Substitui portfolio_assets.total_shares, average_price e total_invested
 * (V28). Gravados, esses numeros so ficavam certos enquanto todo caminho que
 * mexe em transacao lembrasse de recalcula-los.</p>
 *
 * @param totalShares   cotas em carteira; nulo nos tipos lancados por valor total
 * @param averagePrice  preco medio ponderado; nulo nos tipos lancados por valor total
 * @param totalInvested custo da posicao (valor inicial), 2 casas
 */
public record Position(BigDecimal totalShares, BigDecimal averagePrice, BigDecimal totalInvested) {

    public static final Position EMPTY = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    /** Posicao com custo: as telas so mostram essas. */
    public boolean hasInvestment() {
        return totalInvested != null && totalInvested.compareTo(BigDecimal.ZERO) > 0;
    }
}
