package com.primevest.domain.indicator.dto;

import com.primevest.domain.indicator.Indicator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Historico de um indicador de um ticker (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.3): o Atual, a
 * media da empresa (pontos anuais + Atual; nulos fora, zeros dentro) e os pontos do periodo
 * pedido, do mais antigo ao mais recente.
 */
public record IndicatorHistoryDTO(String ticker, Indicator indicator, Indicator.Unit unit, String period,
                                  IndicatorPointDTO current, BigDecimal companyAverage,
                                  List<IndicatorPointDTO> points) {
}
