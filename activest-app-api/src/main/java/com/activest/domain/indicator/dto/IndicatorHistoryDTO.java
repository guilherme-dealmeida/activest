package com.activest.domain.indicator.dto;

import com.activest.domain.indicator.Indicator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Historico de um indicador de um ticker (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.3): o Atual, a
 * media da empresa (pontos anuais + Atual; nulos fora, zeros dentro) e os pontos do periodo
 * pedido, do mais antigo ao mais recente.
 *
 * <p>{@code averages} (docs/planos/PLANO_TELA_ATIVOS.md, 2.5 e D14): a mesma media por janela — os N ultimos
 * exercicios fechados + Atual —, uma por {@link com.activest.domain.indicator.IndicatorWindow}, na ordem 3A, 5A,
 * MAX. {@code companyAverage} = a de MAX (compatibilidade). O web recorta os pontos pela janela.</p>
 */
public record IndicatorHistoryDTO(String ticker, Indicator indicator, Indicator.Unit unit, String period,
                                  IndicatorPointDTO current, BigDecimal companyAverage,
                                  List<WindowAverage> averages, List<IndicatorPointDTO> points) {

    /**
     * @param window   {@code 3A}, {@code 5A} ou {@code MAX}
     * @param fromYear ano do primeiro exercicio da janela ("desde 2021"); nulo sem ponto anual
     * @param value    media dos pontos anuais da janela + Atual; nulo sem nenhum valor
     */
    public record WindowAverage(String window, Integer fromYear, BigDecimal value) {
    }
}
