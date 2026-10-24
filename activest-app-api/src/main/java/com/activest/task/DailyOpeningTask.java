package com.activest.task;

import com.activest.domain.notification.DailyOpeningService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Abertura do dia [B7]: à meia-noite e, com a tarefa marcada para a subida, no primeiro horário do dia com o sistema
 * no ar — a subida roda no máximo uma vez por dia (D37, D38); é idempotente (docs/planos/PLANO_TAREFAS.md, T2).
 */
@Component
@RequiredArgsConstructor
public class DailyOpeningTask implements TaskHandler {

    private final DailyOpeningService service;

    @Override
    public String type() {
        return "DAILY_OPENING";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.run(ctx.reason())));
    }

    @Override
    public boolean needsRecovery() {
        return true;
    }
}
