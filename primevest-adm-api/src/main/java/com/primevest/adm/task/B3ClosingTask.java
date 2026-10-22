package com.primevest.adm.task;

import com.primevest.adm.domain.pricehistory.ClosingPriceService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Fechamento da B3 [A1], com a recuperação na subida [A3] (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class B3ClosingTask implements TaskHandler {

    private final ClosingPriceService service;

    @Override
    public String type() {
        return "B3_CLOSING";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.runB3Closing(ctx.reason())));
    }

    @Override
    public boolean needsRecovery() {
        return service.b3NeedsRecovery();
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
