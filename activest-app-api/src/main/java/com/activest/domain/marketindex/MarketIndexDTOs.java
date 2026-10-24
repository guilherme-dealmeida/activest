package com.activest.domain.marketindex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Respostas de /api/v1/indices (docs/planos/PLANO_INDICES_MERCADO.md, X5). */
public final class MarketIndexDTOs {

    private MarketIndexDTOs() {
    }

    /** Um indice com o primeiro e o ultimo valor da serie (nulos enquanto a carga nao rodou). */
    public record IndexDTO(String code, String name, IndexPeriodicity periodicity, IndexUnit unit,
                           IndexSource source, boolean accumulates, LocalDate firstDate, LocalDate lastDate,
                           BigDecimal lastValue) {
    }

    public record PointDTO(LocalDate date, BigDecimal value) {
    }

    /**
     * Grafico de um card da tela Indices ({@link IndexChart}). {@code firstDate}/{@code lastDate}: a serie inteira
     * (limites do de/ate); {@code from}/{@code to}: a janela mostrada; {@code range}: o atalho ou CUSTOM;
     * {@code displayUnit}: unidade de {@code last}/{@code min}/{@code max}/{@code points} (CDI e Selic anualizados).
     * Indice de taxa traz {@code accumulatedPct}; de nivel, {@code change}/{@code changePct}. {@code view}: nos
     * mensais, MENSAL (variacao do mes) ou ANUAL (acumulado em 12 meses, % a.a.); nulo nos demais.
     */
    public record ChartDTO(String code, String name, IndexPeriodicity periodicity, IndexUnit unit, String displayUnit,
                           IndexSource source, LocalDate firstDate, LocalDate lastDate, LocalDate from, LocalDate to,
                           String range, String interval, IndexChart.View view, BigDecimal last, BigDecimal min, BigDecimal max,
                           BigDecimal change, BigDecimal changePct, BigDecimal accumulatedPct,
                           List<PointDTO> points) {
    }

    public record HistoryDTO(String code, IndexUnit unit, LocalDate from, LocalDate to, List<PointDTO> points) {
    }

    /**
     * Acumulado no periodo. {@code firstDate}/{@code lastDate}: primeiro e ultimo ponto usados (taxa) ou base e fim
     * (nivel); {@code factor} com 10 casas; {@code percent} = (fator − 1) × 100, 6 casas.
     */
    public record AccumulatedDTO(String code, IndexUnit unit, LocalDate from, LocalDate to,
                                 BigDecimal percentOfIndex, BigDecimal spreadPerYear, LocalDate firstDate,
                                 LocalDate lastDate, int periods, BigDecimal factor, BigDecimal percent) {
    }
}
