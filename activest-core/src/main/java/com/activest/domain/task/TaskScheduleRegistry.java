package com.activest.domain.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * Desligado com a chave geral {@code activest.scheduling.enabled} (testes, pilha de teste).</p>
 *
 * <p>Vigência e disparo adiantado (23/10/2026): o agendador mede a espera por um contador da máquina e o cron fala do
 * relógio de parede; os dois se afastam (na VM do Docker Desktop, ~4 s por dia de espera) e o disparo chega segundos
 * antes da hora. A vigência da rodada é o horário nominal que o cron calculou ({@link SlotTrigger}) — não o relógio
 * no disparo —, e a rodada espera o relógio chegar nela antes de começar ({@link #awaitSlot}): o início fica sempre
 * igual ou depois da vigência, e o "hoje" das rotinas (a abertura do dia à meia-noite) é o dia certo.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "activest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class TaskScheduleRegistry implements DisposableBean {

    static final Duration SAFETY_RELOAD = Duration.ofMinutes(5);

    private record Entry(String cron, ScheduledFuture<?> future) {
    }

    /**
     * O {@link CronTrigger} que guarda o horário nominal que calculou. O Spring calcula o próximo só depois que o disparo
     * atual termina, então, durante o disparo, {@link #slot()} é o horário dele — a vigência da rodada.
     */
    static final class SlotTrigger implements Trigger {
        private final CronTrigger cron;
        private volatile Instant slot;

        SlotTrigger(String expression) {
            this.cron = new CronTrigger(expression, TaskTime.ZONE);
        }

        @Override
        public Instant nextExecution(TriggerContext context) {
            Instant next = cron.nextExecution(context);
            slot = next;
            return next;
        }

        Instant slot() {
            return slot;
        }
    }

    private final TaskRepository tasks;
    private final TaskExecutor executor;
    private final String service;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<Long, Entry> scheduled = new ConcurrentHashMap<>();

    public TaskScheduleRegistry(TaskRepository tasks, TaskExecutor executor,
                                @Value("${activest.tasks.service:}") String service) {
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
                SlotTrigger trigger = new SlotTrigger(w.getValue());
                ScheduledFuture<?> f = scheduler.schedule(() -> fire(taskId, trigger.slot()), trigger);
                scheduled.put(taskId, new Entry(w.getValue(), f));
                added++;
            }
        }
        if (added > 0 || removed > 0) {
            log.info("Tarefas: agendamento ({}) — {} agendada(s), {} entrou(aram), {} saiu(ram)", service,
                scheduled.size(), added, removed);
        }
    }

    /** Disparo do horário {@code slot} (o nominal do cron): espera a hora, se chegou cedo, e roda com essa vigência. */
    void fire(Long taskId, Instant slot) {
        Instant at = slot != null ? slot : Instant.now();
        LocalDateTime vigencia = LocalDateTime.ofInstant(at, TaskTime.ZONE);
        Thread.ofVirtual().name("task-" + taskId).start(() -> {
            if (awaitSlot(at)) {
                executor.runScheduled(taskId, TaskTriggerType.SCHEDULE, vigencia);
            }
        });
    }

    /**
     * Segura a rodada até o relógio chegar em {@code slot}. Devolve {@code false} só se a thread for interrompida
     * (o serviço está parando).
     */
    static boolean awaitSlot(Instant slot) {
        Duration early = Duration.between(Instant.now(), slot);
        if (early.isPositive()) {
            log.debug("Tarefas: disparo {} ms antes da vigência {}; esperando", early.toMillis(), slot);
        }
        while (true) {
            Duration left = Duration.between(Instant.now(), slot);
            if (!left.isPositive()) {
                return true;
            }
            try {
                Thread.sleep(left.plusMillis(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
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
