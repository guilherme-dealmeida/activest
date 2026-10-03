package com.primevest.domain.pricehistory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cotacao historica de um ticker para o grafico da tela de emissoras (docs/planos/PLANO_TELA_EMISSORES.md, B5).
 *
 * @param firstDate  primeiro pregao gravado do ticker (limite do "MAX" e dos campos de data); nulo sem preco
 * @param lastDate   ultimo pregao gravado; nulo sem preco
 * @param from       inicio efetivo do periodo pedido (ja limitado a firstDate)
 * @param to         fim efetivo do periodo pedido (ja limitado a lastDate)
 * @param interval   densidade dos pontos: INTRADAY (futuro), DAY, WEEK ou MONTH
 * @param adjusted   algum ponto do periodo foi ajustado por desdobramento, grupamento ou bonificacao (D9)
 * @param closeOnly  so ha fechamento diario (sem preco durante o pregao) — o "1D" mostra dois fechamentos
 * @param change     variacao absoluta do periodo (ultimo - primeiro fechamento, ajustados)
 * @param changePct  variacao em pontos percentuais (12,34 = 12,34%)
 * @param min        menor fechamento diario do periodo (ajustado), mesmo quando os pontos sao semanais/mensais
 * @param max        maior fechamento diario do periodo (ajustado)
 */
public record PriceHistoryDTO(String ticker, String priceTicker, LocalDate firstDate, LocalDate lastDate,
                              LocalDate from, LocalDate to, String range, String interval, boolean adjusted,
                              boolean closeOnly, BigDecimal change, BigDecimal changePct, BigDecimal min,
                              BigDecimal max, List<Point> points) {

    /** Um ponto: {@code time} e a data do pregao (ISO) — no intradiario, data e hora. */
    public record Point(String time, BigDecimal close) {
    }
}
