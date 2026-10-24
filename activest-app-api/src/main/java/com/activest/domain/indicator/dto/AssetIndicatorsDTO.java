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
 * @param basePeriodEnd   data do balanco ou do informe usado; nulo sem nenhum
 * @param statementLayout layout do balanco do emissor ({@code COMERCIAL}, {@code BANCO}, {@code SEGURADORA}; nulo
 *                        no FII): em banco e seguradora os indicadores so do comercial nao vem, e a tela explica
 *                        (docs/planos/PLANO_TELA_ATIVOS.md, D17)
 */
public record AssetIndicatorsDTO(String ticker, Long issuerId, IssuerKind kind, BigDecimal price,
                                 LocalDate basePeriodEnd, String statementLayout, List<Item> indicators) {

    public record Item(Indicator indicator, Indicator.Unit unit, BigDecimal value, String note) {
    }
}
