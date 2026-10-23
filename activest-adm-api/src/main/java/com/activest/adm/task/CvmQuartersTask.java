package com.activest.adm.task;

import com.activest.adm.domain.fundamentals.CompanyQuarterService;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Série trimestral das companhias (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class CvmQuartersTask implements TaskHandler {

    private final CompanyQuarterService service;

    @Override
    public String type() {
        return "CVM_QUARTERS";
    }

    @Override
    public void run(TaskRunContext ctx) {
        var result = service.rebuild(ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("A série trimestral já estava sendo recalculada por outro caminho.");
        }
        ctx.summary(TaskSummaries.of(result));
    }
}
