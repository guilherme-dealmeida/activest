package com.primevest.domain.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Na subida do serviço (docs/planos/PLANO_TAREFAS.md, T2): (1) a rodada que estava {@code RUNNING} nos tipos deste
 * serviço morreu com a instância anterior e vira {@code FAILED}; (2) com o agendamento ligado, a subida (D31, D37,
 * D38) — as tarefas com {@code run_on_startup}, ativas e sem rodada no dia, rodam uma vez com origem {@code STARTUP}
 * se o handler disser que há o que recuperar ({@link TaskHandler#needsRecovery()}), fora da thread de inicialização.
 */
@Slf4j
@Component
public class TaskStartup {

    private final TaskExecutionRepository executions;
    private final TaskTypeRepository types;
    private final TaskExecutor executor;
    private final List<TaskHandler> handlers;
    private final TransactionTemplate tx;
    private final String service;
    private final boolean scheduling;

    public TaskStartup(TaskExecutionRepository executions, TaskTypeRepository types, TaskExecutor executor,
                       List<TaskHandler> handlers,
                       PlatformTransactionManager txManager,
                       @Value("${primevest.tasks.service:}") String service,
                       @Value("${primevest.scheduling.enabled:true}") boolean scheduling) {
        this.executions = executions;
        this.types = types;
        this.executor = executor;
        this.handlers = handlers;
        this.tx = new TransactionTemplate(txManager);
        this.service = service;
        this.scheduling = scheduling;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        failOrphans();
        syncStoppable();
        if (scheduling) {
            // um tipo por thread, como as recuperacoes de antes: uma recuperacao longa nao segura as outras
            handlers.forEach(h -> Thread.ofVirtual().name("recovery-" + h.type()).start(() -> recover(h)));
        }
    }

    void failOrphans() {
        if (service == null || service.isBlank()) {
            return;
        }
        TaskApi api = TaskApi.valueOf(service);
        Integer n = tx.execute(s -> {
            List<TaskExecution> running = executions.findRunningByService(api);
            running.forEach(e -> {
                e.setStatus(TaskExecutionStatus.FAILED);
                e.setFinishedAt(TaskTime.now());
                e.setErrorMessage("Serviço reiniciado durante a execução.");
            });
            executions.saveAll(running);
            return running.size();
        });
        if (n != null && n > 0) {
            log.warn("Tarefas: {} rodada(s) em execucao na instancia anterior marcadas como FAILED", n);
        }
    }

    /** Grava em {@code task_types.stoppable} o que os handlers deste serviço declaram (a tela lê de lá). */
    void syncStoppable() {
        tx.executeWithoutResult(s -> handlers.forEach(h -> types.findByType(h.type()).ifPresent(t -> {
            if (t.isStoppable() != h.stoppable()) {
                t.setStoppable(h.stoppable());
                types.save(t);
            }
        })));
    }

    /**
     * D37, D38: as tarefas do tipo marcadas para a subida, ativas e sem rodada no dia rodam uma vez — se o handler disser
     * que há o que recuperar.
     */
    void recover(TaskHandler handler) {
        try {
            List<Long> ids = executor.startupTasksOf(handler.type());
            if (ids.isEmpty()) {
                return;
            }
            if (!handler.needsRecovery()) {
                log.info("Tarefas: {} em dia, nada a recuperar na subida", handler.type());
                return;
            }
            for (Long taskId : ids) {
                executor.runScheduled(taskId, TaskTriggerType.STARTUP, TaskTime.now());
            }
        } catch (RuntimeException e) {
            log.error("Tarefas: recuperacao na subida de {} falhou: {}", handler.type(), e.getMessage(), e);
        }
    }
}
