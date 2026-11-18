package com.activest.domain.portfolio;

import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.corporateaction.StockEventType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um evento do papel que mexeu numa posicao: o passo do {@link PositionCalculator} com o antes e o depois — a linha
 * do evento no extrato de Transacoes (docs/planos/PLANO_EVENTOS_TRANSACOES.md, D1).
 *
 * <p>Sai do mesmo percurso que calcula a posicao, entao a soma das variacoes do extrato e a quantidade da Carteira
 * nunca divergem.</p>
 *
 * @param eventId        id em {@code asset_stock_events} (nulo em passo montado a mao)
 * @param costBefore     custo da posicao antes, na base da corretora ({@link CostBasis#BROKER}: a bonificacao entra
 *                       sem custo)
 * @param taxUnitCost    custo por acao bonificada para o IR (catalogo ou informado; {@link CostBasis#TAX}); nulo =
 *                       nao informado. Nulo tambem no desdobramento e no grupamento, que nao tem custo
 * @param fractionShares fracao que nao virou cota (a B3 vende em leilao e paga em dinheiro); zero sem fracao
 */
public record AppliedStockEvent(Long eventId, StockEventType type, BigDecimal factor, LocalDate dataCom,
                                BigDecimal sharesBefore, BigDecimal sharesAfter,
                                BigDecimal costBefore, BigDecimal costAfter,
                                BigDecimal taxUnitCost, BigDecimal fractionShares) {

    /** Variacao das cotas: positiva no desdobramento e na bonificacao, negativa no grupamento. */
    public BigDecimal sharesDelta() {
        return sharesAfter.subtract(sharesBefore);
    }

    /**
     * Variacao do custo na base da corretora: zero na bonificacao (as acoes novas entram sem custo); no desdobramento e
     * no grupamento, a parte do custo que saiu com a fracao (negativa).
     */
    public BigDecimal costDelta() {
        return costAfter.subtract(costBefore);
    }

    /** Custo das acoes bonificadas para o IR: novas x custo atribuido. Nulo sem custo ou fora da bonificacao. */
    public BigDecimal taxCostDelta() {
        return taxUnitCost == null ? null : sharesDelta().multiply(taxUnitCost);
    }

    /** Data ex: o pregao seguinte a data-com, quando o evento passa a valer na posicao (D3). */
    public LocalDate exDate() {
        return exDateOf(dataCom);
    }

    /** O pregao seguinte a data-com. */
    public static LocalDate exDateOf(LocalDate dataCom) {
        LocalDate d = dataCom.plusDays(1);
        while (!BusinessCalendar.isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }
}
