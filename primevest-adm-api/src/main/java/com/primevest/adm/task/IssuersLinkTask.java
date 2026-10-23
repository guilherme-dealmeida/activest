package com.primevest.adm.task;

import com.primevest.adm.domain.issuer.IssuerLinkService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ligação ativo → emissor (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class IssuersLinkTask implements TaskHandler {

    private final IssuerLinkService service;

    @Override
    public String type() {
        return "ISSUERS_LINK";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.linkAll(ctx.reason())));
    }
}
