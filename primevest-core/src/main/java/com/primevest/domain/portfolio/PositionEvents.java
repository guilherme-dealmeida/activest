package com.primevest.domain.portfolio;

import com.primevest.domain.corporateaction.StockEventStep;

import java.util.Comparator;
import java.util.List;

/**
 * O que, alem das transacoes, mexe numa posicao: eventos societarios do papel
 * e devolucoes de capital recebidas (docs/planos/PLANO_BRAPI.md, F5 e F6).
 *
 * <p>Os dois vem ordenados por data aqui, uma vez, para o calculo so
 * percorrer.</p>
 */
public record PositionEvents(List<StockEventStep> stockEvents, List<CapitalReturn> capitalReturns) {

    public static final PositionEvents NONE = new PositionEvents(List.of(), List.of());

    public PositionEvents {
        stockEvents = stockEvents == null ? List.of()
                : stockEvents.stream().sorted(Comparator.comparing(StockEventStep::dataCom)).toList();
        capitalReturns = capitalReturns == null ? List.of()
                : capitalReturns.stream().sorted(Comparator.comparing(CapitalReturn::date)).toList();
    }

    public static PositionEvents ofStockEvents(List<StockEventStep> stockEvents) {
        return new PositionEvents(stockEvents, List.of());
    }

    public boolean isEmpty() {
        return stockEvents.isEmpty() && capitalReturns.isEmpty();
    }
}
