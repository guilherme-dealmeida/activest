package com.activest.domain.task;

import com.activest.support.PostgresContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O executor das tarefas (docs/planos/PLANO_TAREFAS.md, T2) contra o banco de verdade: rodada, falha com mensagem,
 * trava por tipo (SKIPPED), parada cooperativa, atributo obrigatório, órfã na subida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "activest.tasks.service=ADM_API")
@Import({PostgresContainer.class, TaskExecutorTest.Handlers.class})
@ActiveProfiles("test")
class TaskExecutorTest {

    /** Comportamento de cada tipo de teste, trocado por cenário. */
    static volatile Consumer<TaskRunContext> quarters = ctx -> { };
    static volatile Consumer<TaskRunContext> yearly = ctx -> { };

    @TestConfiguration
    static class Handlers {
        @Bean
        TaskHandler quartersHandler() {
            return handler("CVM_QUARTERS", c -> quarters.accept(c));
        }

        @Bean
        TaskHandler yearlyHandler() {
            return handler("B3_YEARLY_QUOTES", c -> yearly.accept(c));
        }

        private static TaskHandler handler(String type, Consumer<TaskRunContext> body) {
            return new TaskHandler() {
                @Override
                public String type() {
                    return type;
                }

                @Override
                public void run(TaskRunContext ctx) {
                    body.accept(ctx);
                }

                @Override
                public boolean stoppable() {
                    return true;
                }
            };
        }
    }

    @Autowired
    private TaskExecutor executor;
    @Autowired
    private TaskExecutionRepository executions;
    @Autowired
    private TaskRepository tasks;
    @Autowired
    private TaskStartup startup;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM task_executions");
        jdbc.update("DELETE FROM notifications");
        quarters = ctx -> { };
        yearly = ctx -> { };
    }

    private TaskExecution waitFinished(Long id) throws InterruptedException {
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            TaskExecution e = executions.findById(id).orElseThrow();
            if (e.getStatus().isFinished()) {
                return e;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("rodada " + id + " nao terminou");
    }

    private Long taskId(String name) {
        return tasks.findAliveByName(name).orElseThrow().getId();
    }

    @Test
    @DisplayName("Rodada manual: RUNNING e depois SUCCEEDED, com o resumo e a cópia dos atributos")
    void success() throws Exception {
        yearly = ctx -> ctx.summary("anos " + ctx.year("FROM_YEAR") + "-" + ctx.year("TO_YEAR"));

        TaskExecution started = executor.runManual(taskId("B3_YEARLY_QUOTES"), null);
        TaskExecution done = waitFinished(started.getId());

        assertThat(done.getStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
        assertThat(done.getTriggerType()).isEqualTo(TaskTriggerType.MANUAL);
        assertThat(done.getSummary()).isEqualTo("anos 2010-null");
        assertThat(done.getParameters()).contains("\"FROM_YEAR\"").contains("2010");
        assertThat(done.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Parâmetros do endpoint por cima dos atributos; a tarefa continua com os dela")
    void endpointParameters() throws Exception {
        yearly = ctx -> ctx.summary("de " + ctx.year("FROM_YEAR"));

        TaskExecution e = waitFinished(executor.runType("B3_YEARLY_QUOTES", null,
            Map.of("FROM_YEAR", "2020")).getId());

        assertThat(e.getSummary()).isEqualTo("de 2020");
        assertThat(jdbc.queryForObject("SELECT ta.att_value FROM task_attributes ta JOIN tasks t ON t.id = ta.task_id "
            + "JOIN task_type_attributes a ON a.id = ta.task_type_attribute_id WHERE t.name = 'B3_YEARLY_QUOTES' "
            + "AND a.att_name = 'FROM_YEAR'", String.class)).isEqualTo("2010");
    }

    @Test
    @DisplayName("Falha: FAILED com o erro e a mensagem ROTINA_FALHOU com o link da rodada (D18)")
    void failure() throws Exception {
        quarters = ctx -> {
            throw new IllegalStateException("fonte fora do ar");
        };

        TaskExecution e = waitFinished(executor.runManual(taskId("CVM_QUARTERS"), null).getId());

        assertThat(e.getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(e.getErrorMessage()).isEqualTo("fonte fora do ar");
        // a mensagem e gravada logo depois de a rodada fechar
        long until = System.currentTimeMillis() + 5_000;
        while (jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'ROTINA_FALHOU'", Long.class) == 0
                && System.currentTimeMillis() < until) {
            Thread.sleep(50);
        }
        String payload = jdbc.queryForObject("SELECT payload::text FROM notifications WHERE kind = 'ROTINA_FALHOU'",
            String.class);
        assertThat(payload).contains("/tasks?tab=monitoring&execution=" + e.getId());
    }

    @Test
    @DisplayName("Trava por tipo (D25): com o tipo em execução, a rodada que chega fica SKIPPED com o motivo (D20)")
    void lockByType() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        quarters = ctx -> {
            running.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        Long id = taskId("CVM_QUARTERS");
        TaskExecution first = executor.runManual(id, null);
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

        // outra tarefa do mesmo tipo tambem fica de fora
        Task copy = new TaskCatalogServiceShim(tasks).copyOf("CVM_QUARTERS", "CVM_QUARTERS_COPY");
        TaskExecution second = executor.runManual(copy.getId(), null);
        assertThat(second.getStatus()).isEqualTo(TaskExecutionStatus.SKIPPED);
        assertThat(second.getErrorMessage()).contains("CVM_QUARTERS").contains("em execucao");
        assertThat(executor.isRunning("CVM_QUARTERS")).isTrue();

        release.countDown();
        assertThat(waitFinished(first.getId()).getStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
        assertThat(executor.isRunning("CVM_QUARTERS")).isFalse();
        jdbc.update("DELETE FROM task_executions WHERE task_id = ?", copy.getId());
        jdbc.update("DELETE FROM tasks WHERE id = ?", copy.getId());
    }

    @Test
    @DisplayName("Stop (D8): a rotina confere o pedido entre unidades e a rodada termina CANCELLED")
    void stopped() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        quarters = ctx -> {
            running.countDown();
            for (int i = 0; i < 200; i++) {
                ctx.checkStop();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        TaskExecution e = executor.runManual(taskId("CVM_QUARTERS"), null);
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
        jdbc.update("UPDATE task_executions SET stop_requested_at = NOW() WHERE id = ?", e.getId());

        assertThat(waitFinished(e.getId()).getStatus()).isEqualTo(TaskExecutionStatus.CANCELLED);
    }

    @Test
    @DisplayName("Rotina que acha a mesma rotina rodando por outro caminho: SKIPPED com o motivo")
    void skippedInside() throws Exception {
        quarters = ctx -> {
            throw new TaskSkippedException("ja rodando pela carga inicial");
        };
        TaskExecution e = waitFinished(executor.runManual(taskId("CVM_QUARTERS"), null).getId());
        assertThat(e.getStatus()).isEqualTo(TaskExecutionStatus.SKIPPED);
        assertThat(e.getErrorMessage()).isEqualTo("ja rodando pela carga inicial");
    }

    @Test
    @DisplayName("Atributo obrigatório vazio: o pedido manual é recusado antes de criar a rodada")
    void required() {
        assertThatThrownBy(() -> executor.runType("B3_YEARLY_QUOTES", null, java.util.Collections.singletonMap(
            "FROM_YEAR", null))).isInstanceOf(TaskInputException.class).hasMessageContaining("FROM_YEAR");
        assertThatThrownBy(() -> executor.runType("B3_YEARLY_QUOTES", null, Map.of("FROM_YEAR", "dois mil")))
            .isInstanceOf(TaskInputException.class);
        assertThat(executions.count()).isZero();
    }

    @Test
    @DisplayName("Subida: a rodada RUNNING dos tipos deste serviço vira FAILED; a de outro serviço fica")
    void orfas() {
        Long adm = taskId("CVM_QUARTERS");
        Long app = taskId("POSITIONS_REFRESH");
        String insert = "INSERT INTO task_executions (task_id, status, trigger_type, scheduled_for, started_at) "
            + "VALUES (?, 'RUNNING', 'SCHEDULE', NOW(), NOW())";
        jdbc.update(insert, adm);
        jdbc.update(insert, app);

        startup.failOrphans();

        assertThat(jdbc.queryForObject("SELECT status FROM task_executions WHERE task_id = ?", String.class, adm))
            .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM task_executions WHERE task_id = ?", String.class, app))
            .isEqualTo("RUNNING");
        jdbc.update("DELETE FROM task_executions");
    }

    @Test
    @DisplayName("Log da rodada (D6): linhas INFO/WARN com o id da rodada, inclusive de thread filha; teto de 2.000")
    void runLog() throws Exception {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("teste.rotina");
        yearly = ctx -> {
            Thread child = new Thread(TaskRun.propagate(() -> log.info("linha da thread filha")));
            child.start();
            try {
                child.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (int i = 0; i < TaskLogCapture.MAX_LINES + 5; i++) {
                log.info("linha {}", i);
            }
            log.warn("aviso depois do teto");
            log.debug("debug nao entra");
        };
        TaskExecution e = waitFinished(executor.runManual(taskId("B3_YEARLY_QUOTES"), null).getId());

        long until = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < until && jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_execution_logs WHERE execution_id = ? AND level = 'WARN'", Long.class,
                e.getId()) == 0) {
            Thread.sleep(100);
        }
        Thread.sleep(1_500); // o lote seguinte (contagem do omitido)
        long lines = jdbc.queryForObject("SELECT COUNT(*) FROM task_execution_logs WHERE execution_id = ?",
            Long.class, e.getId());
        // inicio + filha + 1998 linhas = 2000; depois: 8 INFO omitidas (linhas 1998..2004 e "concluida") e o WARN entra
        assertThat(lines).isEqualTo(TaskLogCapture.MAX_LINES + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution_logs WHERE execution_id = ? "
            + "AND message = 'linha da thread filha'", Long.class, e.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT log_lines_omitted FROM task_executions WHERE id = ?", Integer.class,
            e.getId())).isEqualTo(8);
    }

    @Test
    @DisplayName("Stop pelo ponto de parada estático (TaskRun.checkStop) que os services usam")
    void stoppedByService() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        quarters = ctx -> {
            running.countDown();
            for (int i = 0; i < 200; i++) {
                TaskRun.checkStop();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        TaskExecution e = executor.runManual(taskId("CVM_QUARTERS"), null);
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
        jdbc.update("UPDATE task_executions SET stop_requested_at = NOW() WHERE id = ?", e.getId());
        assertThat(waitFinished(e.getId()).getStatus()).isEqualTo(TaskExecutionStatus.CANCELLED);
        TaskRun.checkStop(); // fora de uma rodada: nao faz nada
    }

    @Test
    @DisplayName("Subida (D37, D38): só tarefa marcada e ativa, no máximo uma vez por dia")
    void onStartup() throws Exception {
        TaskHandler handler = new TaskHandler() {
            @Override
            public String type() {
                return "CVM_QUARTERS";
            }

            @Override
            public void run(TaskRunContext ctx) {
            }
        };
        Long id = taskId("CVM_QUARTERS");
        assertThat(executor.startupTasksOf("CVM_QUARTERS")).isEmpty(); // nao marcada

        jdbc.update("UPDATE tasks SET run_on_startup = TRUE WHERE id = ?", id);
        try {
            assertThat(executor.startupTasksOf("CVM_QUARTERS")).containsExactly(id);
            startup.recover(handler);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_executions WHERE task_id = ? "
                + "AND trigger_type = 'STARTUP'", Long.class, id)).isEqualTo(1);

            // reiniciar no mesmo dia nao repete
            assertThat(executor.startupTasksOf("CVM_QUARTERS")).isEmpty();
            startup.recover(handler);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_executions WHERE task_id = ?", Long.class, id))
                .isEqualTo(1);

            // rodada de ontem nao conta
            jdbc.update("UPDATE task_executions SET started_at = started_at - INTERVAL '1 day' WHERE task_id = ?", id);
            assertThat(executor.startupTasksOf("CVM_QUARTERS")).containsExactly(id);

            // desligada nao roda na subida
            jdbc.update("UPDATE tasks SET enabled = FALSE WHERE id = ?", id);
            assertThat(executor.startupTasksOf("CVM_QUARTERS")).isEmpty();
        } finally {
            jdbc.update("UPDATE tasks SET run_on_startup = FALSE, enabled = TRUE WHERE id = ?", id);
        }
    }

    /** Cria uma segunda tarefa do mesmo tipo direto pelo repositório (o cadastro tem teste próprio). */
    private record TaskCatalogServiceShim(TaskRepository tasks) {
        Task copyOf(String original, String name) {
            Task source = tasks.findAliveByName(original).orElseThrow();
            Task copy = new Task();
            copy.setName(name);
            copy.setTaskType(source.getTaskType());
            return tasks.save(copy);
        }
    }
}
