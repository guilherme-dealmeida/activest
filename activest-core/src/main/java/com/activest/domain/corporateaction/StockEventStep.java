package com.activest.domain.corporateaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um evento societario como passo do calculo da posicao — sem JPA, para o
 * calculo e os testes nao dependerem de entidade.
 *
 * @param dataCom      data-com: o evento vale a partir do pregao seguinte (data ex)
 * @param type         desdobramento, grupamento ou bonificacao
 * @param factor       como a B3 publica (ver {@link StockEventType})
 * @param assignedCost custo por acao bonificada para o IR (catalogo ou informado); nulo = nao informado. Entra no custo
 *                     so na base da Receita; na da corretora a bonificacao entra sem custo
 *                     (portfolio.CostBasis, docs/planos/PLANO_EVENTOS_TRANSACOES.md, secao 8.4)
 * @param eventId      id em {@code asset_stock_events}; nulo nos testes que montam o passo a mao. Serve a linha do
 *                     evento no extrato de Transacoes (docs/planos/PLANO_EVENTOS_TRANSACOES.md)
 */
public record StockEventStep(LocalDate dataCom, StockEventType type, BigDecimal factor, BigDecimal assignedCost,
                             Long eventId) {

    public StockEventStep(LocalDate dataCom, StockEventType type, BigDecimal factor, BigDecimal assignedCost) {
        this(dataCom, type, factor, assignedCost, null);
    }
}
