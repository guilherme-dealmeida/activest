package com.primevest.domain.task;

import com.primevest.exception.BusinessException;
import com.primevest.support.JpaTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cadastro das tarefas contra o banco de verdade (V61/V62): espelho, edição e as duas formas de exclusão (O5). */
@Import(TaskCatalogService.class)
class TaskCatalogServiceTest extends JpaTestBase {

    @Autowired
    private TaskCatalogService service;
    @Autowired
    private TaskRepository tasks;
    @Autowired
    private TaskExecutionRepository executions;
    @Autowired
    private EntityManager em;

    private TaskCatalogService.TaskChanges named(String name) {
        return new TaskCatalogService.TaskChanges(name, null, null, null, null);
    }

    @Test
    @DisplayName("Tarefa nova recebe os atributos do tipo com o padrão, e pode mudar o valor na criação")
    void mirror() {
        Task task = service.create("B3_YEARLY_QUOTES", new TaskCatalogService.TaskChanges("B3_YEARLY_QUOTES_2020",
            "Recarga desde 2020", null, null, Map.of("FROM_YEAR", "2020")));
        em.flush();
        em.clear();

        Task saved = tasks.findAliveByName("B3_YEARLY_QUOTES_2020").orElseThrow();
        assertThat(saved.getAttributes()).hasSize(2);
        assertThat(saved.attribute("FROM_YEAR").orElseThrow().getAttValue()).isEqualTo("2020");
        assertThat(saved.attribute("TO_YEAR").orElseThrow().getAttValue()).isNull();
        assertThat(saved.getEffectiveDescription()).isEqualTo("Recarga desde 2020");
        assertThat(saved.getId()).isEqualTo(task.getId());
    }

    @Test
    @DisplayName("Edição valida nome, horário e o valor pelo tipo; padrão mudado no tipo não mexe na tarefa")
    void edit() {
        Long id = tasks.findAliveByName("MARKET_INDICES").orElseThrow().getId();

        service.update(id, new TaskCatalogService.TaskChanges("MARKET_INDICES_DAILY", null, "0 15 10 * * *", false,
            Map.of("INDEX_CODES", " CDI,IPCA ")));
        em.flush();
        em.clear();
        Task task = tasks.findAlive(id).orElseThrow();
        assertThat(task.getName()).isEqualTo("MARKET_INDICES_DAILY");
        assertThat(task.getCron()).isEqualTo("0 15 10 * * *");
        assertThat(task.isEnabled()).isFalse();
        assertThat(task.attribute("INDEX_CODES").orElseThrow().getAttValue()).isEqualTo("CDI,IPCA");
        assertThat(task.getEffectiveDescription()).isEqualTo(task.getTaskType().getDescription());

        assertThatThrownBy(() -> service.update(id, named("indices diarios"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(id, named("B3_CLOSING")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Já existe");
        assertThatThrownBy(() -> service.update(id, new TaskCatalogService.TaskChanges(null, null, "toda hora", null,
            null))).isInstanceOf(BusinessException.class).hasMessageContaining("Horário");
        assertThatThrownBy(() -> service.update(id, new TaskCatalogService.TaskChanges(null, null, null, null,
            Map.of("FROM_YEAR", "dez")))).isInstanceOf(BusinessException.class).hasMessageContaining("FROM_YEAR");
        assertThatThrownBy(() -> service.update(id, new TaskCatalogService.TaskChanges(null, null, null, null,
            Map.of("FNET_REREAD_ALL", "true")))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("não tem o atributo");
    }

    @Test
    @DisplayName("Excluir: sem rodadas apaga de vez; com rodadas, exclusão lógica e o nome fica livre; o tipo fica")
    void exclusion() {
        Task withoutRun = service.create("CVM_QUARTERS", named("CVM_QUARTERS_COPY"));
        service.delete(withoutRun.getId());
        em.flush();
        assertThat(tasks.findById(withoutRun.getId())).isEmpty();

        Task withRun = tasks.findAliveByName("CVM_QUARTERS").orElseThrow();
        TaskExecution e = new TaskExecution();
        e.setTask(withRun);
        e.setStatus(TaskExecutionStatus.SUCCEEDED);
        e.setTriggerType(TaskTriggerType.MANUAL);
        e.setScheduledFor(TaskTime.now());
        e.setStartedAt(TaskTime.now());
        e.setFinishedAt(TaskTime.now());
        executions.save(e);
        service.delete(withRun.getId());
        em.flush();
        em.clear();

        assertThat(tasks.findAlive(withRun.getId())).isEmpty();
        assertThat(tasks.findById(withRun.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(executions.existsByTaskId(withRun.getId())).isTrue();
        assertThat(service.create("CVM_QUARTERS", named("CVM_QUARTERS")).getId()).isNotEqualTo(withRun.getId());
    }
}
