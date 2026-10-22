package com.primevest.adm.task;

import com.primevest.adm.domain.pricehistory.TreasuryPriceService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskInputException;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSkippedException;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Preço oficial do Tesouro Direto [A10]; sem primeiro ano, a rodada diária (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class TreasuryPricesTask implements TaskHandler {

    private final TreasuryPriceService service;

    @Override
    public String type() {
        return "TREASURY_PRICES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        Integer from = ctx.year("FROM_YEAR");
        if (from != null && from < 2002) {
            throw new TaskInputException("O histórico do Tesouro começa em 2002.");
        }
        var result = from == null ? service.importRecent(ctx.reason()) : service.importFrom(from, ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("O preço do Tesouro já estava sendo carregado por outro caminho.");
        }
        ctx.summary(TaskSummaries.of(result));
    }

    @Override
    public boolean needsRecovery() {
        return service.needsRecovery();
    }
}
