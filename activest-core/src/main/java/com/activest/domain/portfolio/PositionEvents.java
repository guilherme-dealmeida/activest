package com.activest.domain.portfolio;

import com.activest.domain.corporateaction.StockEventStep;

import java.util.Comparator;
import java.util.List;

/**
 * O que, alem das transacoes, mexe numa posicao: eventos societarios do papel,
 * devolucoes de capital recebidas (docs/planos/PLANO_BRAPI.md, F5 e F6) e as cotas
 * retidas no come-cotas dos fundos (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, C9/C10).
 *
 * <p>Os dois vem ordenados por data aqui, uma vez, para o calculo so
 * percorrer.</p>
 */
public record PositionEvents(List<StockEventStep> stockEvents, List<CapitalReturn> capitalReturns,
                             List<ShareWithholding> withholdings) {

    public static final PositionEvents NONE = new PositionEvents(List.of(), List.of());

    public PositionEvents {
        stockEvents = stockEvents == null ? List.of()
                : stockEvents.stream().sorted(Comparator.comparing(StockEventStep::dataCom)).toList();
        capitalReturns = capitalReturns == null ? List.of()
                : capitalReturns.stream().sorted(Comparator.comparing(CapitalReturn::date)).toList();
        withholdings = withholdings == null ? List.of()
                : withholdings.stream().sorted(Comparator.comparing(ShareWithholding::date)).toList();
    }

    public PositionEvents(List<StockEventStep> stockEvents, List<CapitalReturn> capitalReturns) {
        this(stockEvents, capitalReturns, List.of());
    }

    /** Os mesmos eventos com as cotas retidas do fundo (come-cotas). */
    public PositionEvents withWithholdings(List<ShareWithholding> list) {
        return new PositionEvents(stockEvents, capitalReturns, list);
    }

    public static PositionEvents ofStockEvents(List<StockEventStep> stockEvents) {
        return new PositionEvents(stockEvents, List.of());
    }

    public boolean isEmpty() {
        return stockEvents.isEmpty() && capitalReturns.isEmpty() && withholdings.isEmpty();
    }
}
