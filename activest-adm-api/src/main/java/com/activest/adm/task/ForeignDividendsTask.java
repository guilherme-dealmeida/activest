package com.activest.adm.task;

import com.activest.adm.domain.foreign.ForeignCorporateEventsService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Proventos e desdobramentos dos ativos internacionais, rodada diária (docs/planos/PLANO_PROVENTOS_INTERNACIONAIS.md,
 * D8): os meses da janela de ex-date de −30 a +90 dias. Ter–sáb 03:30, recuperação na subida, no máximo uma rodada
 * automática por dia.
 */
@Component
@RequiredArgsConstructor
public class ForeignDividendsTask implements TaskHandler {

    private final ForeignCorporateEventsService service;

    @Override
    public String type() {
        return "FOREIGN_DIVIDENDS";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ForeignCorporateEventsService.Result r = service.importRecent(ctx.reason());
        if (r == null) {
            throw new TaskSkippedException("Os proventos internacionais já estavam sendo carregados por outra rodada.");
        }
        ctx.summary(TaskSummaries.of(r));
    }

    @Override
    public boolean needsRecovery() {
        return service.stale();
    }

    @Override
    public boolean oncePerDay() {
        return true;
    }

    @Override
    public boolean stoppable() {
        return true;
    }
}
