package com.activest.domain.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.domain.notification.NotificationDraft;
import com.activest.domain.notification.NotificationKind;
import com.activest.domain.notification.NotificationService;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Executa as rodadas das tarefas (docs/planos/PLANO_TAREFAS.md, T2): uma linha em {@code task_executions} por rodada,
 * trava por tipo (D25 — a que chega com o tipo em execução vira {@code SKIPPED}, D20), cópia dos atributos usados,
 * status final, resumo e erro, e a mensagem {@code ROTINA_FALHOU} com link para a rodada (D18).
 *
 * <p>Roda só os tipos que têm {@link TaskHandler} neste serviço. O MDC {@value #MDC_KEY} marca as linhas de log da
 * rodada (D6).</p>
 */
@Slf4j
@Service("taskRunExecutor") // "taskExecutor" e o apelido do executor de threads do Spring Boot
public class TaskExecutor {

    public static final String MDC_KEY = "taskExecutionId";

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final long STOP_CHECK_MILLIS = 2_000;

    private final TaskRepository tasks;
    private final TaskExecutionRepository executions;
    private final NotificationService notifications;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Map<String, TaskHandler> handlers;
    private final org.springframework.beans.factory.ObjectProvider<TaskLogCapture> logCapture;
    private final Set<String> runningTypes = ConcurrentHashMap.newKeySet();

    public TaskExecutor(TaskRepository tasks, TaskExecutionRepository executions, NotificationService notifications,
                        ObjectMapper json, PlatformTransactionManager txManager, List<TaskHandler> handlers,
                        org.springframework.beans.factory.ObjectProvider<TaskLogCapture> logCapture) {
        this.logCapture = logCapture;
        this.tasks = tasks;
        this.executions = executions;
        this.notifications = notifications;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
        this.handlers = handlers.stream().collect(Collectors.toMap(TaskHandler::type, Function.identity()));
    }

    /** Resultado de uma rodada síncrona: a linha e o que a rotina deixou em {@link TaskRunContext#result(Object)}. */
    public record Outcome(TaskExecution execution, Object result) {
    }

    public Optional<TaskHandler> handler(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    public boolean handles(String type) {
        return handlers.containsKey(type);
    }

    /** Tipo cuja rodada está em execução neste serviço. */
    public boolean isRunning(String type) {
        return runningTypes.contains(type);
    }

    /** Run da tela (D23): em segundo plano, com os atributos da tarefa. */
    public TaskExecution runManual(Long taskId, UUID requestedBy) {
        TaskSnapshot task = snapshot(taskId);
        return execute(task, TaskTriggerType.MANUAL, TaskTime.now(), requestedBy, Map.of(), false).execution();
    }

    /**
     * Disparo pelos endpoints de administrador de hoje (D16, D22): a primeira tarefa do tipo, com os parâmetros do
     * endpoint por cima dos atributos dela. Em segundo plano.
     */
    public TaskExecution runType(String type, UUID requestedBy, Map<String, String> overrides) {
        return execute(firstOfType(type), TaskTriggerType.MANUAL, TaskTime.now(), requestedBy, overrides, false)
            .execution();
    }

    /** Como {@link #runType}, na thread de quem chamou; a falha da rotina volta como exceção (a curadoria). */
    public Outcome runTypeSync(String type, UUID requestedBy, Map<String, String> overrides) {
        return execute(firstOfType(type), TaskTriggerType.MANUAL, TaskTime.now(), requestedBy, overrides, true);
    }

    /** Rodada pelo horário (ou pela subida), na thread de quem chamou. Tarefa desligada não roda e não gera linha. */
    public void runScheduled(Long taskId, TaskTriggerType trigger, LocalDateTime scheduledFor) {
        TaskSnapshot task;
        try {
            task = snapshot(taskId);
        } catch (ResourceNotFoundException e) {
            log.info("Tarefa {}: nao existe mais, rodada {} nao executada", taskId, trigger);
            return;
        }
        if (!task.active()) {
            log.info("Tarefa {}: desligada, rodada {} nao executada", task.name(), trigger);
            return;
        }
        if (trigger != TaskTriggerType.MANUAL && isOncePerDay(task.type()) && ranAutomaticallyToday(task.id())) {
            log.info("Tarefa {}: ja teve rodada automatica hoje, rodada {} nao executada (uma por dia)", task.name(),
                trigger);
            return;
        }
        try {
            execute(task, trigger, scheduledFor, null, Map.of(), true);
        } catch (RuntimeException e) {
            // a rodada ja ficou registrada como FAILED (com a mensagem): o agendador nao precisa saber
            log.debug("Tarefa {}: rodada {} terminou com erro: {}", task.name(), trigger, e.getMessage());
        }
    }

    /** Rodada pelo horário de todas as tarefas ativas e com horário de um tipo (as classes @Scheduled de hoje). */
    public void runScheduledType(String type) {
        for (Long id : scheduledTasksOf(type)) {
            runScheduled(id, TaskTriggerType.SCHEDULE, TaskTime.now());
        }
    }

    /** Ids das tarefas vivas de um tipo que estão ativas e têm horário. */
    public List<Long> scheduledTasksOf(String type) {
        return tasks.findAliveByType(type).stream().filter(t -> t.isActive() && t.getCron() != null)
            .map(Task::getId).toList();
    }

    /**
     * Ids das tarefas de um tipo que rodam nesta subida (D37, D38): marcadas para a subida, ativas e sem rodada no dia
     * (no fuso de São Paulo) — reiniciar o serviço não repete a rodada.
     */
    public List<Long> startupTasksOf(String type) {
        LocalDateTime now = TaskTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        if (isOncePerDay(type)) {
            // Uma automatica por dia: a subida so substitui a agendada perdida (horario de hoje ja passou)
            return tasks.findAliveByType(type).stream()
                .filter(t -> t.isActive() && t.isRunOnStartup())
                .filter(t -> scheduledTimePassed(t.getCron(), now))
                .filter(t -> !ranAutomaticallyToday(t.getId()))
                .map(Task::getId).toList();
        }
        return tasks.findAliveByType(type).stream()
            .filter(t -> t.isActive() && t.isRunOnStartup())
            .filter(t -> !executions.existsByTaskIdAndStartedAtGreaterThanEqual(t.getId(), today))
            .map(Task::getId).toList();
    }

    private boolean isOncePerDay(String type) {
        TaskHandler h = handlers.get(type);
        return h != null && h.oncePerDay();
    }

    private boolean ranAutomaticallyToday(Long taskId) {
        return executions.existsByTaskIdAndTriggerTypeInAndStartedAtGreaterThanEqual(taskId,
            List.of(TaskTriggerType.SCHEDULE, TaskTriggerType.STARTUP), TaskTime.now().toLocalDate().atStartOfDay());
    }

    /**
     * O primeiro horário do cron hoje já passou? Sem cron (tarefa sem horário), a subida pode rodar a qualquer hora.
     * Cron que não dispara hoje (ex.: só dias úteis num sábado) não roda na subida.
     */
    static boolean scheduledTimePassed(String cron, LocalDateTime now) {
        if (cron == null || cron.isBlank()) {
            return true;
        }
        LocalDateTime first = org.springframework.scheduling.support.CronExpression.parse(cron)
            .next(now.toLocalDate().atStartOfDay().minusSeconds(1));
        return first != null && first.toLocalDate().equals(now.toLocalDate()) && !first.isAfter(now);
    }

    private TaskSnapshot firstOfType(String type) {
        Long id = tasks.findAliveByType(type).stream().findFirst().map(Task::getId)
            .orElseThrow(() -> new ResourceNotFoundException("Nenhuma tarefa do tipo " + type));
        return snapshot(id);
    }

    /** O que a rodada precisa da tarefa, lido numa transação (a rodada roda fora dela). */
    private record TaskSnapshot(Long id, String name, String type, boolean typeEnabled, boolean active,
                                Map<String, String> values, List<TaskTypeAttribute> definitions) {
    }

    private TaskSnapshot snapshot(Long taskId) {
        return tx.execute(s -> {
            Task t = tasks.findAlive(taskId).orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
            Map<String, String> values = new LinkedHashMap<>();
            List<TaskTypeAttribute> defs = new java.util.ArrayList<>();
            t.getAttributes().stream()
                .sorted(java.util.Comparator.comparing((TaskAttribute x) -> x.getTypeAttribute().getPosition())
                    .thenComparing(x -> x.getTypeAttribute().getId()))
                .forEach(a -> {
                values.put(a.getTypeAttribute().getAttName(), a.getAttValue());
                defs.add(a.getTypeAttribute());
            });
            return new TaskSnapshot(t.getId(), t.getName(), t.getTaskType().getType(), t.getTaskType().isEnabled(),
                t.isActive(), values, defs);
        });
    }

    private Outcome execute(TaskSnapshot task, TaskTriggerType trigger, LocalDateTime scheduledFor, UUID requestedBy,
                            Map<String, String> overrides, boolean sync) {
        String type = task.type();
        TaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("O tipo " + type + " nao roda neste servico");
        }
        if (trigger == TaskTriggerType.MANUAL && !task.typeEnabled()) {
            throw new BusinessException("O tipo " + type + " está desligado.");
        }

        Map<String, String> values = new LinkedHashMap<>(task.values());
        overrides.forEach((k, v) -> values.put(k, TaskAttributeValues.normalize(v)));
        String inputError = validate(task, values);
        if (inputError != null && trigger == TaskTriggerType.MANUAL) {
            throw new TaskInputException(inputError);
        }

        LocalDateTime now = TaskTime.now();
        if (inputError == null && !runningTypes.add(type)) {
            String since = executions.findRunningByType(type).stream().findFirst()
                .map(e -> " (" + e.getTask().getName() + ", desde " + e.getStartedAt().format(HOUR) + ")")
                .orElse("");
            TaskExecution skipped = save(task, trigger, scheduledFor, requestedBy, values, TaskExecutionStatus.SKIPPED,
                now, "Ja havia uma rodada do tipo " + type + " em execucao" + since + ".");
            log.info("Tarefa {}: SKIPPED — tipo {} ja em execucao{}", task.name(), type, since);
            return new Outcome(skipped, null);
        }
        if (inputError != null) {
            // agendada com atributo invalido: registra a falha, nao roda
            TaskExecution failed = save(task, trigger, scheduledFor, requestedBy, values, TaskExecutionStatus.FAILED,
                now, inputError);
            notifyFailure(task, failed.getId(), inputError);
            return new Outcome(failed, null);
        }

        TaskExecution execution;
        try {
            execution = save(task, trigger, scheduledFor, requestedBy, values, TaskExecutionStatus.RUNNING, null, null);
        } catch (RuntimeException e) {
            runningTypes.remove(type);
            throw e;
        }
        TaskRunContext ctx = new TaskRunContext(execution.getId(), task.name(), trigger, values,
            stopChecker(execution.getId()));
        String taskName = task.name();

        if (!sync) {
            Thread.ofVirtual().name("task-" + taskName).start(() -> body(handler, ctx, task, type, taskName, false));
            return new Outcome(execution, null);
        }
        return body(handler, ctx, task, type, taskName, true);
    }

    private Outcome body(TaskHandler handler, TaskRunContext ctx, TaskSnapshot task, String type, String taskName,
                         boolean rethrow) {
        Long id = ctx.executionId();
        MDC.put(MDC_KEY, String.valueOf(id));
        TaskRun.bind(ctx);
        try {
            log.info("Tarefa {}: inicio ({})", taskName, ctx.reason());
            handler.run(ctx);
            TaskExecution done = finish(id, TaskExecutionStatus.SUCCEEDED, ctx.summary(), null);
            log.info("Tarefa {}: concluida", taskName);
            return new Outcome(done, ctx.result());
        } catch (TaskStoppedException e) {
            log.info("Tarefa {}: encerrada a pedido", taskName);
            return new Outcome(finish(id, TaskExecutionStatus.CANCELLED, ctx.summary(), e.getMessage()), null);
        } catch (TaskSkippedException e) {
            log.info("Tarefa {}: SKIPPED — {}", taskName, e.getMessage());
            return new Outcome(finish(id, TaskExecutionStatus.SKIPPED, ctx.summary(), e.getMessage()), null);
        } catch (RuntimeException e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("Tarefa {} falhou: {}", taskName, error, e);
            // a mensagem antes de fechar a rodada: quem ve FAILED ja encontra a mensagem
            notifyFailure(task, id, error);
            TaskExecution failed = finish(id, TaskExecutionStatus.FAILED, ctx.summary(), error);
            if (rethrow) {
                throw e;
            }
            return new Outcome(failed, null);
        } finally {
            runningTypes.remove(type);
            TaskRun.unbind();
            MDC.remove(MDC_KEY);
            logCapture.ifAvailable(c -> c.close(id));
        }
    }

    /** Obrigatórios preenchidos e valores no formato do tipo; {@code null} = tudo certo. */
    private static String validate(TaskSnapshot task, Map<String, String> values) {
        for (TaskTypeAttribute def : task.definitions()) {
            String value = values.get(def.getAttName());
            if (def.isRequired() && TaskAttributeValues.normalize(value) == null) {
                return "Atributo " + def.getAttName() + " é obrigatório.";
            }
            String error = TaskAttributeValues.validate(def.getValueType(), value);
            if (error != null) {
                return "Atributo " + def.getAttName() + ": " + error + ".";
            }
        }
        return null;
    }

    private TaskExecution save(TaskSnapshot task, TaskTriggerType trigger, LocalDateTime scheduledFor, UUID requestedBy,
                               Map<String, String> values, TaskExecutionStatus status, LocalDateTime finishedAt,
                               String error) {
        return tx.execute(s -> {
            TaskExecution e = new TaskExecution();
            e.setTask(tasks.getReferenceById(task.id()));
            e.setStatus(status);
            e.setTriggerType(trigger);
            e.setRequestedBy(requestedBy);
            e.setScheduledFor(scheduledFor);
            e.setStartedAt(TaskTime.now());
            e.setFinishedAt(finishedAt);
            e.setParameters(toJson(values));
            e.setErrorMessage(error);
            return executions.save(e);
        });
    }

    private TaskExecution finish(Long id, TaskExecutionStatus status, String summary, String error) {
        return tx.execute(s -> {
            TaskExecution e = executions.findById(id).orElseThrow();
            e.setStatus(status);
            e.setFinishedAt(TaskTime.now());
            e.setSummary(summary);
            e.setErrorMessage(error);
            return executions.save(e);
        });
    }

    private BooleanSupplier stopChecker(Long id) {
        long[] lastCheck = {0};
        boolean[] requested = {false};
        return () -> {
            if (requested[0]) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastCheck[0] >= STOP_CHECK_MILLIS) {
                lastCheck[0] = now;
                requested[0] = executions.findStopRequestedAt(id) != null;
            }
            return requested[0];
        };
    }

    private String toJson(Map<String, String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** D18: a mensagem de rotina que falhou, agora com o link da rodada. Uma por tarefa por dia (R10). */
    private void notifyFailure(TaskSnapshot task, Long executionId, String error) {
        try {
            notifications.notify(NotificationDraft.of(NotificationKind.ROTINA_FALHOU)
                .title("Tarefa " + task.name() + " falhou")
                .body("A rodada terminou com erro: " + error + "\nO resumo e o log estão na tela de Tarefas. A "
                    + "próxima rodada tenta de novo.")
                .ref("TASK", task.id())
                .key(task.name() + ":" + NotificationService.today())
                .link("Ver rodada", "/tasks?tab=monitoring&execution=" + executionId)
                .build());
        } catch (RuntimeException e) {
            log.warn("Tarefa {}: mensagem de falha nao gravada: {}", task.name(), e.getMessage());
        }
    }
}
