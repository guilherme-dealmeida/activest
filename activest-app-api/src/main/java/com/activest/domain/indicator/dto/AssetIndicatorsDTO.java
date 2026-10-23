package com.activest.domain.indicator.dto;

import com.activest.domain.indicator.Indicator;
import com.activest.domain.issuer.IssuerKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Indicadores atuais de um ticker: ultimo balanco (acao) ou ultimo informe (FII) com o preco
 * atual.
 *
 * @param basePeriodEnd data do balanco ou do informe usado; nulo sem nenhum
 */
public record AssetIndicatorsDTO(String ticker, Long issuerId, IssuerKind kind, BigDecimal price,
                                 LocalDate basePeriodEnd, List<Item> indicators) {

    public record Item(Indicator indicator, Indicator.Unit unit, BigDecimal value, String note) {
    }
}
