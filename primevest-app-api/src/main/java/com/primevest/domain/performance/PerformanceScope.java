package com.primevest.domain.performance;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * O recorte da analise (docs/planos/PLANO_RENTABILIDADE.md, 4.1, L9): tipo -> categoria -> ativo (a posicao; na renda
 * fixa, o titulo), com ou sem a reserva, na base bruta ou liquida. Campos nulos = sem filtro.
 */
public record PerformanceScope(String typeName, Long categoryId, UUID positionId, String ticker,
                               ReservationFilter reservation, PerformanceBasis basis) {

    public PerformanceScope {
        reservation = reservation != null ? reservation : ReservationFilter.WITH;
        basis = basis != null ? basis : PerformanceBasis.NET;
    }

    public static PerformanceScope all() {
        return new PerformanceScope(null, null, null, null, ReservationFilter.WITH, PerformanceBasis.NET);
    }

    public Predicate<PortfolioTimeline.PositionSeries> filter() {
        return p -> (typeName == null || typeName.equalsIgnoreCase(p.typeName()))
                && (categoryId == null || categoryId.equals(p.categoryId()))
                && (positionId == null || positionId.equals(p.id()))
                && (ticker == null || ticker.equalsIgnoreCase(p.ticker()))
                && (reservation == ReservationFilter.WITH || !p.reservation());
    }
}
