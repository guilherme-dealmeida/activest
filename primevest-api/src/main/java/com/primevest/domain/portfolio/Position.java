package com.primevest.domain.portfolio;

import java.math.BigDecimal;

/**
 * Posicao apurada a partir das transacoes. Nunca e gravada.
 *
 * <p>Substitui portfolio_assets.total_shares, average_price e total_invested
 * (V28). Gravados, esses numeros so ficavam certos enquanto todo caminho que
 * mexe em transacao lembrasse de recalcula-los.</p>
 *
 * <p>Nos tipos lancados por valor total (Renda Fixa) o custo NAO desconta as
 * vendas: {@code totalInvested} e o valor aplicado (a soma das compras) e
 * {@code totalSold} e o quanto ja foi resgatado. As duas pontas ficam
 * separadas porque as regras E e F comparam uma com a outra — "aplicado +
 * valorizacao nao pode ser menor que o resgatado" nao faz sentido se o
 * resgatado ja tiver sido abatido do aplicado. O saldo e
 * aplicado + valorizacao - resgatado.</p>
 *
 * <p>Nos demais tipos, o custo continua caindo na venda pelo preco medio do
 * momento, e {@code totalSold} e zero: ali o que sai da posicao sao cotas, nao
 * valor.</p>
 *
 * @param totalShares   cotas em carteira; nulo nos tipos lancados por valor total
 * @param averagePrice  preco medio ponderado; nulo nos tipos lancados por valor total
 * @param totalInvested custo da posicao nos tipos por cota; valor aplicado nos tipos por valor, 2 casas
 * @param totalSold     valor resgatado nos tipos por valor; zero nos demais, 2 casas
 */
public record Position(BigDecimal totalShares, BigDecimal averagePrice, BigDecimal totalInvested,
                       BigDecimal totalSold) {

    public static final Position EMPTY =
        new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    /** Posicao com custo: as telas so mostram essas. */
    public boolean hasInvestment() {
        return totalInvested != null && totalInvested.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal sold() {
        return totalSold != null ? totalSold : BigDecimal.ZERO;
    }

    public BigDecimal invested() {
        return totalInvested != null ? totalInvested : BigDecimal.ZERO;
    }

    public BigDecimal shares() {
        return totalShares != null ? totalShares : BigDecimal.ZERO;
    }
}
