package com.primevest.task;

import com.primevest.domain.integration.IntegrationService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Sincronização diária das corretoras [B4]: só enfileira; o worker da fila busca (T2). */
@Component
@RequiredArgsConstructor
public class IntegrationsDailySyncTask implements TaskHandler {

    private final IntegrationService service;

    @Override
    public String type() {
        return "INTEGRATIONS_DAILY_SYNC";
    }

    @Override
    public void run(TaskRunContext ctx) {
        int n = service.enqueueScheduled();
        ctx.summary(n + " conexao(oes) na fila da sincronizacao diaria");
    }
}
