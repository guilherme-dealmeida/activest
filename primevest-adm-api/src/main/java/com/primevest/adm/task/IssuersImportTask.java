package com.primevest.adm.task;

import com.primevest.adm.domain.issuer.IssuerImportService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
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
