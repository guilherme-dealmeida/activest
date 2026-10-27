package com.activest.domain.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cotas retidas de uma posicao de fundo no come-cotas (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, C9 e C10): no fim do
 * dia {@code date} a quantidade cai {@code shares}; o aplicado (custo) fica — o imposto antecipado e saida de valor, nao
 * resgate.
 */
public record ShareWithholding(LocalDate date, BigDecimal shares) {
}
