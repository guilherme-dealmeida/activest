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
 * @param assignedCost custo por acao bonificada; nulo = zero provisorio (B27)
 */
public record StockEventStep(LocalDate dataCom, StockEventType type, BigDecimal factor, BigDecimal assignedCost) {
}
