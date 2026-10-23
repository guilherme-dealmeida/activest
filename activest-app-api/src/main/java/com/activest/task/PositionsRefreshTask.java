package com.activest.task;

import com.activest.domain.portfolio.PositionRefreshService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rede de segurança da carteira [B1]. A verificação da subida [B2] continua no {@code PositionRefreshService}, fora
 * das tarefas: roda em todo perfil, inclusive nos testes (docs/planos/PLANO_TAREFAS.md, T2).
 */
@Component
@RequiredArgsConstructor
public class PositionsRefreshTask implements TaskHandler {

    private final PositionRefreshService service;

    @Override
    public String type() {
        return "POSITIONS_REFRESH";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.refreshAll(ctx.reason())));
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
