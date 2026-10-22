package com.primevest.domain.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Agendamento pelo banco (docs/planos/PLANO_TAREFAS.md, T3, D10 e D11): cada tarefa ativa (ela e o tipo ligados, não
 * excluída) com horário, dos tipos que este serviço executa, ganha um {@link CronTrigger} no fuso de São Paulo. Relê
 * as tarefas ao receber "tarefa mudou" ({@link #reload()}) e a cada 5 minutos — a rede de segurança do Redis (D29).
 *
 * <p>O disparo só abre uma thread virtual e chama o {@link TaskExecutor}: uma rodada longa não segura o agendador.
 * Desligado com a chave geral {@code primevest.scheduling.enabled} (testes, pilha de teste).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class TaskScheduleRegistry implements DisposableBean {

    static final Duration SAFETY_RELOAD = Duration.ofMinutes(5);

    private record Entry(String cron, ScheduledFuture<?> future) {
    }

    private final TaskRepository tasks;
    private final TaskExecutor executor;
    private final String service;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<Long, Entry> scheduled = new ConcurrentHashMap<>();

    public TaskScheduleRegistry(TaskRepository tasks, TaskExecutor executor,
                                @Value("${primevest.tasks.service:}") String service) {
        this.tasks = tasks;
        this.executor = executor;
        this.service = service;
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("task-cron-");
        scheduler.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
        scheduler.scheduleWithFixedDelay(this::safeReload, Instant.now().plus(SAFETY_RELOAD), SAFETY_RELOAD);
    }

    private void safeReload() {
        try {
            reload();
        } catch (RuntimeException e) {
            log.warn("Tarefas: releitura do agendamento falhou: {}", e.getMessage());
        }
    }

    /** Lê as tarefas deste serviço e acerta os agendamentos (cancela o que mudou ou saiu, agenda o que entrou). */
    public synchronized void reload() {
        if (service == null || service.isBlank()) {
            return;
        }
        Map<Long, String> wanted = new HashMap<>();
        for (Task t : tasks.findAliveByService(TaskApi.valueOf(service))) {
            if (t.isActive() && t.getCron() != null && executor.handles(t.getTaskType().getType())) {
                wanted.put(t.getId(), t.getCron());
            }
        }
        int removed = 0;
        int added = 0;
        for (var it = scheduled.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!e.getValue().cron().equals(wanted.get(e.getKey()))) {
                e.getValue().future().cancel(false);
                it.remove();
                removed++;
            }
        }
        for (var w : wanted.entrySet()) {
            if (!scheduled.containsKey(w.getKey())) {
                Long taskId = w.getKey();
                ScheduledFuture<?> f = scheduler.schedule(() -> fire(taskId),
                    new CronTrigger(w.getValue(), TaskTime.ZONE));
                scheduled.put(taskId, new Entry(w.getValue(), f));
                added++;
            }
        }
        if (added > 0 || removed > 0) {
            log.info("Tarefas: agendamento ({}) — {} agendada(s), {} entrou(aram), {} saiu(ram)", service,
                scheduled.size(), added, removed);
        }
    }

    private void fire(Long taskId) {
        // o horario da rodada: o cron dispara no segundo 0 do minuto
        LocalDateTime slot = TaskTime.now().truncatedTo(ChronoUnit.MINUTES);
        Thread.ofVirtual().name("task-" + taskId).start(
            () -> executor.runScheduled(taskId, TaskTriggerType.SCHEDULE, slot));
    }

    /** Tarefas agendadas agora, com o cron (para os testes e a conferência). */
    public Map<Long, String> scheduledCrons() {
        Map<Long, String> m = new HashMap<>();
        scheduled.forEach((id, e) -> m.put(id, e.cron()));
        return m;
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }
}
