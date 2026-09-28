package com.primevest.domain.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Devolucao de capital recebida pela posicao: amortizacao de FII ou
 * restituicao de capital de empresa (PLANO_BRAPI.md, B13).
 *
 * <p>Sai do custo da posicao na data de pagamento. A quantidade de cotas nao
 * muda; o preco medio cai. O custo nunca fica negativo — o que passar do custo
 * e ganho.</p>
 *
 * @param date   data de pagamento (a data-com, quando a fonte nao traz o pagamento)
 * @param amount valor total recebido pela posicao
 */
public record CapitalReturn(LocalDate date, BigDecimal amount) {
}
