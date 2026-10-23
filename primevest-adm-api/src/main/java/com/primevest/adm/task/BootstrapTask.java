package com.primevest.adm.task;

import com.primevest.adm.domain.admin.BootstrapService;
import com.primevest.domain.task.TaskSkippedException;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Carga inicial num banco vazio (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class BootstrapTask implements TaskHandler {

    private final BootstrapService service;

    @Override
    public String type() {
        return "BOOTSTRAP";
    }

    @Override
    public void run(TaskRunContext ctx) {
        var result = service.run(ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("Já existe uma carga inicial em andamento.");
        }
        ctx.summary(result.steps().size() + " etapas em " + result.seconds() + " s; "
            + result.steps().stream().filter(s -> !s.ok()).count() + " com falha");
        if (!result.ok()) {
            throw new IllegalStateException("Carga inicial terminou com etapa em falha (detalhes no log da rodada).");
        }
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
