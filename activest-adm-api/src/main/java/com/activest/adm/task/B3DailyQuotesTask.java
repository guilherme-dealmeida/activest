package com.activest.adm.task;

import com.activest.adm.domain.pricehistory.HistoricalQuotesImportService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
import com.activest.domain.task.TaskTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Cotações do catálogo pelo COTAHIST diário [A11]: sem primeiro pregão, do seguinte ao último gravado até hoje; com
 * ele, o período (até 366 dias). Recuperação na subida quando falta o pregão do último dia útil
 * (docs/planos/PLANO_TAREFAS.md, T2).
 */
@Component
@RequiredArgsConstructor
public class B3DailyQuotesTask implements TaskHandler {

    private final HistoricalQuotesImportService service;

    @Override
    public String type() {
        return "B3_DAILY_QUOTES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        LocalDate from = ctx.date("FROM_DATE");
        if (from == null) {
            ctx.summary(TaskSummaries.of(service.importRecent(ctx.reason())));
            return;
        }
        LocalDate to = ctx.date("TO_DATE") != null ? ctx.date("TO_DATE") : LocalDate.now(TaskTime.ZONE);
        if (to.isBefore(from) || from.isBefore(to.minusDays(366))) {
            throw new TaskInputException("Período inválido: " + from + " a " + to + " (até 366 dias).");
        }
        ctx.summary(TaskSummaries.of(service.importDays(from, to, ctx.reason())));
    }

    @Override
    public boolean needsRecovery() {
        return service.stale();
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
