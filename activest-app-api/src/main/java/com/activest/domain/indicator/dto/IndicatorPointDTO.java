package com.activest.domain.indicator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um ponto de um indicador (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.3).
 *
 * @param label     "2025", "2T26" ou "Atual"
 * @param periodEnd data do balanco (acao) ou ultimo dia do mes do informe (FII)
 * @param value     nulo quando nao se aplica — o motivo vem em {@code note}
 */
public record IndicatorPointDTO(String label, LocalDate periodEnd, BigDecimal value, String note) {
}
