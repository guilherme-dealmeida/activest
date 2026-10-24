package com.activest.adm.task;

import com.activest.adm.domain.pricehistory.TreasuryPriceService;
import com.activest.adm.integration.PublicFileCache;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Preço oficial do Tesouro Direto [A10]; sem primeiro ano, a rodada diária (docs/planos/PLANO_TAREFAS.md, T2). Fonte que
 * recusou ou não respondeu termina {@code FAILED}, mesmo com a cópia em disco lida (docs/planos/PLANO_FONTE_TESOURO.md,
 * D2): a rodada existe para trazer o PU novo.
 */
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
        if (result.sourceProblem() != null) {
            throw new PublicFileCache.UnavailableException(result.sourceProblem(), null);
        }
    }

    @Override
    public boolean needsRecovery() {
        return service.needsRecovery();
    }
}
