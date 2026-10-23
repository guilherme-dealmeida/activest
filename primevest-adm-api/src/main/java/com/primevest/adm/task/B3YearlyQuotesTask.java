package com.primevest.adm.task;

import com.primevest.adm.domain.pricehistory.HistoricalQuotesImportService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Série histórica anual da B3 (COTAHIST), de {@code FROM_YEAR} a {@code TO_YEAR} (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class B3YearlyQuotesTask implements TaskHandler {

    private final HistoricalQuotesImportService service;

    @Override
    public String type() {
        return "B3_YEARLY_QUOTES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        int from = ctx.year("FROM_YEAR");
        int to = TaskYears.to(ctx.year("TO_YEAR"));
        TaskYears.check("COTAHIST", from, to, 1986);
        ctx.summary(TaskSummaries.of(service.importYears(from, to, ctx.reason())));
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
