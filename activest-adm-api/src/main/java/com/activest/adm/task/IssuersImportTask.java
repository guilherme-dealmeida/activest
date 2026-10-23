package com.activest.adm.task;

import com.activest.adm.domain.issuer.IssuerImportService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Emissores (CVM e B3) (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class IssuersImportTask implements TaskHandler {

    private final IssuerImportService service;

    @Override
    public String type() {
        return "ISSUERS_IMPORT";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.importAll(ctx.reason())));
    }
}
