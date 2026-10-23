package com.primevest.domain.task;

import com.primevest.support.PostgresContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agendamento pelo banco (docs/planos/PLANO_TAREFAS.md, T3): só as tarefas ativas, com horário e com handler neste
 * serviço; editar e reler acerta o agendamento; o horário dispara a rodada {@code SCHEDULE}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"primevest.tasks.service=ADM_API", "primevest.scheduling.enabled=true"})
@Import({PostgresContainer.class, TaskScheduleRegistryTest.Handlers.class})
@ActiveProfiles("test")
class TaskScheduleRegistryTest {

    @TestConfiguration
    static class Handlers {
        @Bean
        TaskHandler quartersHandler() {
            return new TaskHandler() {
                @Override
                public String type() {
                    return "CVM_QUARTERS";
                }

                @Override
                public void run(TaskRunContext ctx) {
                    ctx.summary("ok");
                }
            };
        }
    }

    @Autowired
    private TaskScheduleRegistry registry;
    @Autowired
    private TaskCommands commands;
    @Autowired
    private TaskRepository tasks;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void back() {
        jdbc.update("UPDATE tasks SET cron = NULL, enabled = TRUE WHERE name = 'CVM_QUARTERS'");
        jdbc.update("UPDATE task_types SET enabled = TRUE WHERE type = 'CVM_QUARTERS'");
        jdbc.update("DELETE FROM task_executions");
        registry.reload();
    }

    @Test
    @DisplayName("Só agenda tarefa ativa, com horário e com handler aqui; editar e reler acerta o agendamento")
    void schedule() {
        Long id = tasks.findAliveByName("CVM_QUARTERS").orElseThrow().getId();
        Long b3 = tasks.findAliveByName("B3_CLOSING").orElseThrow().getId();
        assertThat(registry.scheduledCrons()).doesNotContainKey(id).doesNotContainKey(b3); // sem cron / sem handler

        jdbc.update("UPDATE tasks SET cron = '0 0 3 * * *' WHERE id = ?", id);
        commands.changed(TaskApi.ADM_API);
        assertThat(registry.scheduledCrons()).containsEntry(id, "0 0 3 * * *");

        jdbc.update("UPDATE tasks SET cron = '0 15 3 * * *' WHERE id = ?", id);
        registry.reload();
        assertThat(registry.scheduledCrons()).containsEntry(id, "0 15 3 * * *");

        jdbc.update("UPDATE tasks SET enabled = FALSE WHERE id = ?", id);
        registry.reload();
        assertThat(registry.scheduledCrons()).doesNotContainKey(id);

        jdbc.update("UPDATE tasks SET enabled = TRUE WHERE id = ?", id);
        jdbc.update("UPDATE task_types SET enabled = FALSE WHERE type = 'CVM_QUARTERS'");
        registry.reload();
        assertThat(registry.scheduledCrons()).doesNotContainKey(id);
    }

    @Test
    @DisplayName("O horário dispara a rodada com origem SCHEDULE, vigência no horário do cron e início depois dela")
    void fires() throws Exception {
        Long id = tasks.findAliveByName("CVM_QUARTERS").orElseThrow().getId();
        jdbc.update("UPDATE tasks SET cron = '* * * * * *' WHERE id = ?", id);
        registry.reload();

        long until = System.currentTimeMillis() + 10_000;
        while (jdbc.queryForObject("SELECT COUNT(*) FROM task_executions WHERE task_id = ? AND status = 'SUCCEEDED' "
                + "AND trigger_type = 'SCHEDULE'", Long.class, id) == 0 && System.currentTimeMillis() < until) {
            Thread.sleep(100);
        }
        jdbc.update("UPDATE tasks SET cron = NULL WHERE id = ?", id);
        registry.reload();
        // a vigência é o horário nominal do cron (segundo cheio), e a rodada nunca começa antes dela
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_executions WHERE task_id = ? AND status = "
            + "'SUCCEEDED' AND trigger_type = 'SCHEDULE'", Long.class, id)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_executions WHERE task_id = ? AND trigger_type = "
            + "'SCHEDULE' AND (scheduled_for <> date_trunc('second', scheduled_for) OR started_at < scheduled_for)",
            Long.class, id)).isZero();
    }

    @Test
    @DisplayName("Disparo adiantado: a rodada espera a vigência e grava o horário nominal, não o do disparo")
    void earlyFire() throws Exception {
        Long id = tasks.findAliveByName("CVM_QUARTERS").orElseThrow().getId();
        // o agendador chegou ~1,5 s antes da hora (o desvio do relógio da VM)
        Instant slot = Instant.now().plusMillis(1_500).truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        registry.fire(id, slot);

        LocalDateTime vigencia = LocalDateTime.ofInstant(slot, TaskTime.ZONE);
        long until = System.currentTimeMillis() + 10_000;
        while (jdbc.queryForObject("SELECT COUNT(*) FROM task_executions WHERE task_id = ? AND status = 'SUCCEEDED' "
                + "AND scheduled_for = ?", Long.class, id, vigencia) == 0 && System.currentTimeMillis() < until) {
            Thread.sleep(100);
        }
        Map<String, Object> e = jdbc.queryForMap("SELECT scheduled_for, started_at, trigger_type FROM task_executions "
            + "WHERE task_id = ? AND status = 'SUCCEEDED' AND scheduled_for = ?", id, vigencia);
        assertThat(((Timestamp) e.get("started_at")).toLocalDateTime()).isAfterOrEqualTo(vigencia);
        assertThat(e.get("trigger_type")).isEqualTo("SCHEDULE");
    }

    @Test
    @DisplayName("awaitSlot segura até a hora; horário que já passou não espera")
    void awaitSlot() {
        Instant slot = Instant.now().plusMillis(400);
        assertThat(TaskScheduleRegistry.awaitSlot(slot)).isTrue();
        assertThat(Instant.now()).isAfterOrEqualTo(slot);

        long before = System.nanoTime();
        assertThat(TaskScheduleRegistry.awaitSlot(Instant.now().minusSeconds(5))).isTrue();
        assertThat(System.nanoTime() - before).isLessThan(100_000_000L);
    }
}
