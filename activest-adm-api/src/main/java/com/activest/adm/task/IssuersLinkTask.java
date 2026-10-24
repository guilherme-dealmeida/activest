package com.activest.adm.task;

import com.activest.adm.domain.issuer.IssuerLinkService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
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
