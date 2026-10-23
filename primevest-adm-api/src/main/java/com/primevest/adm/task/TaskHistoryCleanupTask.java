package com.primevest.adm.task;

import com.primevest.domain.task.TaskExecutionLogRepository;
import com.primevest.domain.task.TaskExecutionRepository;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskInputException;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Limpeza do histórico das tarefas (D7, D34): linhas de log com mais de {@code LOG_RETENTION_DAYS} dias e rodadas
 * terminadas com mais de {@code EXECUTION_RETENTION_DAYS} (as linhas delas vão junto).
 */
@Component
@RequiredArgsConstructor
public class TaskHistoryCleanupTask implements TaskHandler {

    private final TaskExecutionLogRepository logs;
    private final TaskExecutionRepository executions;
    private final TransactionTemplate tx;

    @Override
    public String type() {
        return "TASK_HISTORY_CLEANUP";
    }

    @Override
    public void run(TaskRunContext ctx) {
        int logDays = positive(ctx, "LOG_RETENTION_DAYS");
        int executionDays = positive(ctx, "EXECUTION_RETENTION_DAYS");
        Integer lines = tx.execute(s -> logs.deleteLoggedBefore(TaskTime.now().minusDays(logDays)));
        Integer runs = tx.execute(s -> executions.deleteFinishedBefore(TaskTime.now().minusDays(executionDays)));
        ctx.summary(lines + " linha(s) de log com mais de " + logDays + " dias e " + runs + " rodada(s) com mais de "
            + executionDays + " dias apagadas");
    }

    private static int positive(TaskRunContext ctx, String name) {
        Integer v = ctx.integer(name);
        if (v == null || v < 1) {
            throw new TaskInputException(name + " tem de ser 1 ou mais.");
        }
        return v;
    }
}
