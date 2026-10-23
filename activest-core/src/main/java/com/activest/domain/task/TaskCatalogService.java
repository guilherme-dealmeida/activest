package com.activest.domain.task;

import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Cadastro das tarefas (docs/planos/PLANO_TAREFAS.md, T1): criar com o espelho dos atributos do tipo (O7), editar e
 * excluir — sem rodadas, de vez; com rodadas, exclusão lógica (O5). Criar e excluir ainda não têm tela (D15).
 */
@Service
@RequiredArgsConstructor
public class TaskCatalogService {

    private final TaskRepository tasks;
    private final TaskTypeRepository types;
    private final TaskExecutionRepository executions;

    /** O que se edita numa tarefa; atributo ausente do mapa não muda. */
    public record TaskChanges(String name, String description, String cron, Boolean enabled, Boolean runOnStartup,
                              Map<String, String> attributes) {
        public TaskChanges(String name, String description, String cron, Boolean enabled,
                           Map<String, String> attributes) {
            this(name, description, cron, enabled, null, attributes);
        }
    }

    @Transactional
    public Task create(String type, TaskChanges changes) {
        TaskType taskType = types.findByType(type)
            .orElseThrow(() -> new ResourceNotFoundException("Tipo de tarefa não encontrado: " + type));
        Task task = new Task();
        task.setTaskType(taskType);
        // espelho: uma linha por atributo do tipo, com o valor padrão
        taskType.getAttributes().forEach(a -> task.getAttributes().add(new TaskAttribute(task, a, a.getAttValue())));
        apply(task, changes, true);
        return tasks.save(task);
    }

    @Transactional
    public Task update(Long id, TaskChanges changes) {
        Task task = tasks.findAlive(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
        apply(task, changes, false);
        return tasks.save(task);
    }

    /** Sem rodadas: apaga a linha. Com rodadas: exclusão lógica, o histórico continua. Nunca apaga o tipo. */
    @Transactional
    public void delete(Long id) {
        Task task = tasks.findAlive(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
        if (executions.existsByTaskId(id)) {
            task.setDeletedAt(TaskTime.now());
            tasks.save(task);
        } else {
            tasks.delete(task);
        }
    }

    private void apply(Task task, TaskChanges changes, boolean creating) {
        if (changes.name() != null || creating) {
            String name = changes.name() == null ? null : changes.name().strip();
            if (!TaskNames.isValid(name)) {
                throw new BusinessException("Nome inválido: use letras maiúsculas sem acento, números e _ "
                    + "(ex.: B3_DAILY_QUOTES_RELOAD), até " + TaskNames.MAX_LENGTH + " caracteres.");
            }
            boolean taken = !name.equals(task.getName()) && tasks.existsByNameAndDeletedAtIsNull(name);
            if (taken) {
                throw new BusinessException("Já existe uma tarefa com o nome " + name + ".");
            }
            task.setName(name);
        }
        if (changes.description() != null || creating) {
            task.setDescription(TaskAttributeValues.normalize(changes.description()));
        }
        if (changes.cron() != null || creating) {
            String cron = TaskAttributeValues.normalize(changes.cron());
            if (cron != null && !CronExpression.isValidExpression(cron)) {
                throw new BusinessException("Horário inválido: " + cron);
            }
            task.setCron(cron);
        }
        if (changes.enabled() != null) {
            task.setEnabled(changes.enabled());
        }
        if (changes.runOnStartup() != null) {
            task.setRunOnStartup(changes.runOnStartup());
        }
        Map<String, String> values = changes.attributes() == null ? Map.of() : changes.attributes();
        for (String attName : values.keySet()) {
            if (task.attribute(attName).isEmpty()) {
                throw new BusinessException("O tipo " + task.getTaskType().getType() + " não tem o atributo "
                    + attName + ".");
            }
        }
        for (TaskAttribute attribute : task.getAttributes()) {
            TaskTypeAttribute def = attribute.getTypeAttribute();
            String value = values.containsKey(def.getAttName())
                ? TaskAttributeValues.normalize(values.get(def.getAttName()))
                : attribute.getAttValue();
            String error = TaskAttributeValues.validate(def.getValueType(), value);
            if (error != null) {
                throw new BusinessException("Atributo " + def.getAttName() + ": " + error + ".");
            }
            attribute.setAttValue(value);
        }
    }
}
