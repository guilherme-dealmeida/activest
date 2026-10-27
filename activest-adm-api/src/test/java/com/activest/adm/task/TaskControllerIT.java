package com.activest.adm.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.domain.user.User;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** API da tela de Tarefas (docs/planos/PLANO_TAREFAS.md, T5), de ponta a ponta na adm-api. */
class TaskControllerIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper json;

    private User admin;
    private User common;

    @BeforeEach
    void users() {
        admin = userWithPortfolio("admin-tarefas@activest.test");
        jdbc.update("UPDATE users SET is_admin = TRUE WHERE id = ?", admin.getId());
        common = userWithPortfolio("comum-tarefas@activest.test");
    }

    @AfterEach
    void back() {
        jdbc.update("UPDATE tasks SET name = 'CVM_QUARTERS', cron = NULL, enabled = TRUE, description = NULL, "
            + "run_on_startup = FALSE "
            + "WHERE name IN ('CVM_QUARTERS', 'CVM_QUARTERS_REBUILD')");
        jdbc.update("DELETE FROM task_executions");
    }

    private JsonNode body(ResponseEntity<String> r) throws Exception {
        return json.readTree(r.getBody());
    }

    private long taskId(String name) {
        return jdbc.queryForObject("SELECT id FROM tasks WHERE name = ? AND deleted_at IS NULL", Long.class, name);
    }

    private String waitStatus(long executionId) throws InterruptedException {
        long until = System.currentTimeMillis() + 15_000;
        String status = null;
        while (System.currentTimeMillis() < until) {
            status = jdbc.queryForObject("SELECT status FROM task_executions WHERE id = ?", String.class, executionId);
            if (!"RUNNING".equals(status)) {
                return status;
            }
            Thread.sleep(100);
        }
        return status;
    }

    @Test
    @DisplayName("Só administrador: sem token 401, usuário comum 403")
    void access() {
        assertThat(rest.getForEntity("/api/v1/tasks", String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(common, "/api/v1/tasks", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(common, "/api/v1/tasks/" + taskId("CVM_QUARTERS") + "/run", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Configuração: as 28 tarefas (com a GOALS_SNAPSHOT das metas e a EXCHANGE_RATES do câmbio) com tipo, horário, próxima execução e atributos; os tipos com os atributos aceitos")
    void configuration() throws Exception {
        JsonNode list = body(get(admin, "/api/v1/tasks", String.class));
        assertThat(list.size()).isEqualTo(28);
        JsonNode yearly = null;
        JsonNode closing = null;
        for (JsonNode t : list) {
            if (t.get("name").asText().equals("B3_YEARLY_QUOTES")) {
                yearly = t;
            }
            if (t.get("name").asText().equals("B3_CLOSING")) {
                closing = t;
            }
        }
        assertThat(yearly.get("attributes").size()).isEqualTo(2);
        assertThat(yearly.get("attributes").get(0).get("name").asText()).isEqualTo("FROM_YEAR");
        assertThat(yearly.get("attributes").get(0).get("value").asText()).isEqualTo("2010");
        assertThat(yearly.has("cron")).isFalse();
        assertThat(closing.get("cron").asText()).isEqualTo("0 0 19 * * MON-FRI");
        assertThat(closing.get("nextRun").asText()).isNotBlank();
        assertThat(closing.get("stoppable").asBoolean()).isTrue();
        assertThat(closing.get("runOnStartup").asBoolean()).isTrue();
        assertThat(yearly.get("runOnStartup").asBoolean()).isFalse();

        JsonNode types = body(get(admin, "/api/v1/tasks/types", String.class));
        assertThat(types.size()).isEqualTo(28);
    }

    @Test
    @DisplayName("Editar: nome, descrição, horário, ligado e atributo; nome inválido ou repetido 422")
    void edit() throws Exception {
        long id = taskId("CVM_QUARTERS");
        ResponseEntity<String> r = put(admin, "/api/v1/tasks/" + id, Map.of("name", "CVM_QUARTERS_REBUILD",
            "description", "Recalcula a série", "cron", "0 0 4 * * SUN", "enabled", false, "runOnStartup", true),
            String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode t = body(r);
        assertThat(t.get("name").asText()).isEqualTo("CVM_QUARTERS_REBUILD");
        assertThat(t.get("effectiveDescription").asText()).isEqualTo("Recalcula a série");
        assertThat(t.get("cron").asText()).isEqualTo("0 0 4 * * SUN");
        assertThat(t.get("enabled").asBoolean()).isFalse();
        assertThat(t.get("runOnStartup").asBoolean()).isTrue();
        assertThat(t.has("nextRun")).isFalse();

        assertThat(put(admin, "/api/v1/tasks/" + id, Map.of("name", "com espaço"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(put(admin, "/api/v1/tasks/" + id, Map.of("name", "B3_CLOSING"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(put(admin, "/api/v1/tasks/" + taskId("B3_YEARLY_QUOTES"),
            Map.of("attributes", Map.of("FROM_YEAR", "dez")), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // cron vazio tira o horario
        JsonNode cleared = body(put(admin, "/api/v1/tasks/" + id, Map.of("cron", ""), String.class));
        assertThat(cleared.has("cron")).isFalse();
    }

    @Test
    @DisplayName("Run → rodada no Acompanhamento com resumo e log; Stop numa rodada que já terminou 422")
    void runAndMonitoring() throws Exception {
        long id = taskId("CVM_QUARTERS");
        ResponseEntity<String> r = post(admin, "/api/v1/tasks/" + id + "/run", null, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long executionId = body(r).get("executionId").asLong();
        assertThat(waitStatus(executionId)).isEqualTo("SUCCEEDED");

        JsonNode rows = body(get(admin, "/api/v1/tasks/executions", String.class));
        boolean found = false;
        boolean scheduled = false;
        for (JsonNode e : rows) {
            if (e.has("id") && e.get("id").asLong() == executionId) {
                found = true;
                assertThat(e.get("triggerType").asText()).isEqualTo("MANUAL");
                assertThat(e.get("requestedBy").asText()).isNotBlank();
            }
            scheduled |= e.get("status").asText().equals("SCHEDULED");
        }
        assertThat(found).isTrue();
        assertThat(scheduled).isTrue();

        JsonNode detail = body(get(admin, "/api/v1/tasks/executions/" + executionId, String.class));
        assertThat(detail.get("execution").get("summary").asText()).contains("quarters=");

        Thread.sleep(1_500); // o lote do log
        JsonNode logs = body(get(admin, "/api/v1/tasks/executions/" + executionId + "/logs", String.class));
        assertThat(logs.get("total").asLong()).isPositive();
        assertThat(logs.get("lines").get(0).get("message").asText()).contains("CVM_QUARTERS");

        assertThat(post(admin, "/api/v1/tasks/executions/" + executionId + "/stop", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Filtro de status com vários valores: só agendadas (Agendamentos) ou só as rodadas marcadas (Acompanhamento)")
    void filtroDeStatus() throws Exception {
        long id = taskId("CVM_QUARTERS");
        long executionId = body(post(admin, "/api/v1/tasks/" + id + "/run", null, String.class))
            .get("executionId").asLong();
        assertThat(waitStatus(executionId)).isEqualTo("SUCCEEDED");

        JsonNode scheduled = body(get(admin, "/api/v1/tasks/executions?status=SCHEDULED", String.class));
        assertThat(scheduled.size()).isPositive();
        for (JsonNode e : scheduled) {
            assertThat(e.get("status").asText()).isEqualTo("SCHEDULED");
        }

        JsonNode runs = body(get(admin, "/api/v1/tasks/executions?status=SUCCEEDED,FAILED", String.class));
        assertThat(runs.size()).isPositive();
        for (JsonNode e : runs) {
            assertThat(e.get("status").asText()).isIn("SUCCEEDED", "FAILED");
        }

        JsonNode none = body(get(admin, "/api/v1/tasks/executions?status=CANCELLED", String.class));
        assertThat(none.size()).isZero();
    }

    @Test
    @DisplayName("Run de tarefa da app-api sem mensagens entre os serviços: 503, nenhuma rodada")
    void remoteRunWithoutMessages() {
        ResponseEntity<String> r = post(admin, "/api/v1/tasks/" + taskId("POSITIONS_REFRESH") + "/run", null,
            String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
