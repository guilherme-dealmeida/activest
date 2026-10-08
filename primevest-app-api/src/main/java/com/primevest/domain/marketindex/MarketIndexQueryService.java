package com.primevest.domain.marketindex;

import com.primevest.domain.marketindex.MarketIndexDTOs.AccumulatedDTO;
import com.primevest.domain.marketindex.MarketIndexDTOs.ChartDTO;
import com.primevest.domain.pricehistory.PriceSeries.Interval;
import com.primevest.domain.pricehistory.PriceSeries.Range;
import com.primevest.domain.marketindex.MarketIndexDTOs.HistoryDTO;
import com.primevest.domain.marketindex.MarketIndexDTOs.IndexDTO;
import com.primevest.domain.marketindex.MarketIndexDTOs.PointDTO;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Leitura dos indices de mercado (docs/planos/PLANO_INDICES_MERCADO.md, X5; D7: endpoints, sem tela). So le o
 * catalogo: quem grava a serie e a adm-api.
 *
 * <p>Acumulado: taxa diaria soma os dias com data entre {@code from} e {@code to} (inclusive); taxa mensal soma os
 * meses inteiros do mes de {@code from} ao de {@code to} (pro rata fica para a valorizacao da renda fixa, D9);
 * nivel (Ibovespa, IFIX, dolar) compara o ultimo valor ate {@code to} com o ultimo ate {@code from}. TR e meta
 * Selic nao acumulam.</p>
 */
@Service
@Transactional(readOnly = true)
public class MarketIndexQueryService {

    private final MarketIndexRepository indices;
    private final MarketIndexValueRepository values;

    public MarketIndexQueryService(MarketIndexRepository indices, MarketIndexValueRepository values) {
        this.indices = indices;
        this.values = values;
    }

    public List<IndexDTO> list() {
        return indices.findByActiveTrueOrderByIdAsc().stream().map(i -> {
            Optional<MarketIndexValue> first = values.findFirstByIndexIdOrderByReferenceDateAsc(i.getId());
            Optional<MarketIndexValue> last = values.findFirstByIndexIdOrderByReferenceDateDesc(i.getId());
            return new IndexDTO(i.getCode(), i.getName(), i.getPeriodicity(), i.getUnit(), i.getSource(),
                    i.getUnit().isCompounding() || i.getUnit().isLevel(),
                    first.map(MarketIndexValue::getReferenceDate).orElse(null),
                    last.map(MarketIndexValue::getReferenceDate).orElse(null),
                    last.map(MarketIndexValue::getValue).orElse(null));
        }).toList();
    }

    public HistoryDTO history(String code, LocalDate from, LocalDate to) {
        MarketIndex index = find(code);
        checkRange(from, to);
        List<PointDTO> points = values.findByIndexIdAndReferenceDateBetweenOrderByReferenceDate(index.getId(), from, to)
                .stream().map(v -> new PointDTO(v.getReferenceDate(), v.getValue())).toList();
        return new HistoryDTO(index.getCode(), index.getUnit(), from, to, points);
    }

    public AccumulatedDTO accumulated(String code, LocalDate from, LocalDate to, BigDecimal percentOfIndex,
                                      BigDecimal spreadPerYear) {
        MarketIndex index = find(code);
        checkRange(from, to);
        IndexUnit unit = index.getUnit();
        MarketIndexCalculator.Accumulated a;
        if (unit.isCompounding()) {
            if (percentOfIndex != null && percentOfIndex.signum() <= 0) {
                throw new BusinessException("Percentual do índice deve ser positivo");
            }
            LocalDate start = index.getPeriodicity() == IndexPeriodicity.MENSAL ? from.withDayOfMonth(1) : from;
            List<IndexPoint> rates = values.findByIndexIdAndReferenceDateBetweenOrderByReferenceDate(index.getId(),
                    start, to).stream().map(IndexPoint::of).toList();
            if (rates.isEmpty()) {
                throw new BusinessException("Sem valores de " + index.getCode() + " no período");
            }
            a = MarketIndexCalculator.compound(unit, rates, percentOfIndex, spreadPerYear);
        } else if (unit.isLevel()) {
            if (percentOfIndex != null || spreadPerYear != null) {
                throw new BusinessException("Percentual e spread só se aplicam a índice de taxa");
            }
            IndexPoint base = values.findFirstByIndexIdAndReferenceDateLessThanEqualOrderByReferenceDateDesc(
                    index.getId(), from).map(IndexPoint::of)
                    .orElseThrow(() -> new BusinessException("Sem valor de " + index.getCode() + " até " + from));
            IndexPoint end = values.findFirstByIndexIdAndReferenceDateLessThanEqualOrderByReferenceDateDesc(
                    index.getId(), to).map(IndexPoint::of).orElseThrow();
            a = MarketIndexCalculator.level(unit, base, end);
        } else {
            throw new BusinessException(index.getCode() + " não acumula: "
                    + (unit == IndexUnit.PCT_ANO ? "é uma meta anual, não um rendimento"
                    : "cada valor é a taxa de um período de um mês que começa no dia"));
        }
        return new AccumulatedDTO(index.getCode(), unit, from, to, percentOfIndex, spreadPerYear, a.firstDate(),
                a.lastDate(), a.periods(), a.factor(), a.percent());
    }

    /**
     * Grafico de um card da tela Indices: {@code range} (1D, 7D, 1M, 6M, YTD, 1A, 5A, MAX; padrao 1A) ou o periodo
     * especifico {@code from}/{@code to} (ver {@link IndexChart}). {@code view} so nos mensais: MENSAL (padrao) ou
     * ANUAL (acumulado em 12 meses).
     */
    public ChartDTO chart(String code, String range, LocalDate from, LocalDate to, String view) {
        MarketIndex index = find(code);
        boolean monthlyIndex = index.getPeriodicity() == IndexPeriodicity.MENSAL;
        IndexChart.View v = null;
        if (view != null && !view.isBlank()) {
            try {
                v = IndexChart.View.valueOf(view.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Visão inválida: " + view + " (MENSAL ou ANUAL)");
            }
            if (!monthlyIndex) {
                throw new BusinessException(index.getCode() + " não é mensal: a visão mensal/anual não se aplica");
            }
        }
        if (monthlyIndex && v == null) {
            v = IndexChart.View.MENSAL;
        }
        boolean annual = v == IndexChart.View.ANUAL;
        boolean custom = from != null || to != null;
        Range r = null;
        if (!custom) {
            try {
                r = range == null || range.isBlank() ? Range.Y1 : Range.parse(range);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Período inválido: " + range);
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("Data inicial depois da final");
        }
        if (r != null && index.getPeriodicity() == IndexPeriodicity.MENSAL && (r == Range.D1 || r == Range.D7)) {
            throw new BusinessException(index.getCode() + " é mensal: 1D e 7D não se aplicam");
        }
        String label = custom ? "CUSTOM" : r.label();
        IndexUnit unit = index.getUnit();
        NavigableMap<LocalDate, java.math.BigDecimal> series = new TreeMap<>();
        values.findByIndexIdAndReferenceDateBetweenOrderByReferenceDate(index.getId(), LocalDate.of(1900, 1, 1),
                LocalDate.of(2999, 12, 31)).forEach(x -> series.put(x.getReferenceDate(), x.getValue()));
        String displayUnit = annual ? "% a.a." : IndexChart.displayUnit(unit);
        if (series.isEmpty()) {
            return new ChartDTO(index.getCode(), index.getName(), index.getPeriodicity(), unit,
                    displayUnit, index.getSource(), null, null, null, null, label,
                    Interval.DAY.name(), v, null, null, null, null, null, null, List.of());
        }
        LocalDate first = series.firstKey();
        LocalDate last = series.lastKey();
        LocalDate start;
        LocalDate end;
        if (custom) {
            start = from == null || from.isBefore(first) ? first : from;
            end = to == null || to.isAfter(last) ? last : to;
            if (index.getPeriodicity() == IndexPeriodicity.MENSAL) {
                start = start.withDayOfMonth(1);
            }
        } else {
            IndexChart.Window w = IndexChart.window(index.getPeriodicity(), unit, r, series);
            start = w.start();
            end = w.end();
        }
        NavigableMap<LocalDate, java.math.BigDecimal> window = start.isAfter(end)
                ? new TreeMap<>() : series.subMap(start, true, end, true);
        Interval interval = IndexChart.interval(index.getPeriodicity(), r, start, end);
        IndexChart.Summary s = IndexChart.summary(unit, window);
        List<PointDTO> points;
        java.math.BigDecimal lastShown = s.last();
        java.math.BigDecimal min = s.min();
        java.math.BigDecimal max = s.max();
        if (annual) {
            // acumulado em 12 meses de cada mes da janela (os 11 anteriores vem da serie inteira)
            NavigableMap<LocalDate, java.math.BigDecimal> twelve = start.isAfter(end)
                    ? new TreeMap<>() : IndexChart.rolling12(series).subMap(start, true, end, true);
            points = twelve.entrySet().stream().map(e -> new PointDTO(e.getKey(), e.getValue())).toList();
            lastShown = twelve.isEmpty() ? null : twelve.lastEntry().getValue();
            min = twelve.values().stream().min(java.math.BigDecimal::compareTo).orElse(null);
            max = twelve.values().stream().max(java.math.BigDecimal::compareTo).orElse(null);
        } else {
            points = IndexChart.points(unit, window, interval).stream()
                    .map(e -> new PointDTO(e.getKey(), e.getValue())).toList();
        }
        return new ChartDTO(index.getCode(), index.getName(), index.getPeriodicity(), unit,
                displayUnit, index.getSource(), first, last,
                window.isEmpty() ? start : window.firstKey(), window.isEmpty() ? end : window.lastKey(), label,
                interval.name(), v, lastShown, min, max, s.change(), s.changePct(), s.accumulatedPct(), points);
    }

    private MarketIndex find(String code) {
        return indices.findByCode(code == null ? "" : code.trim().toUpperCase())
                .filter(MarketIndex::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Índice não encontrado: " + code));
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("Período inválido: " + from + " depois de " + to);
        }
    }
}
