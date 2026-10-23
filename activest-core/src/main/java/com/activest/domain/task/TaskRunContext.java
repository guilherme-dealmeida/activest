package com.activest.domain.task;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * O que a rotina recebe numa rodada: os atributos (cópia do início da rodada), a origem, o Stop e onde deixar o
 * resumo. Uma instância por rodada.
 */
public final class TaskRunContext {

    private final Long executionId;
    private final String taskName;
    private final TaskTriggerType trigger;
    private final Map<String, String> values;
    private final BooleanSupplier stopRequested;
    private volatile String summary;
    private volatile Object result;

    public TaskRunContext(Long executionId, String taskName, TaskTriggerType trigger, Map<String, String> values,
                          BooleanSupplier stopRequested) {
        this.executionId = executionId;
        this.taskName = taskName;
        this.trigger = trigger;
        this.values = Collections.unmodifiableMap(values);
        this.stopRequested = stopRequested;
    }

    public Long executionId() {
        return executionId;
    }

    public String taskName() {
        return taskName;
    }

    public TaskTriggerType trigger() {
        return trigger;
    }

    /** Texto para os logs das rotinas, no lugar do antigo "19:00" / "sob demanda". */
    public String reason() {
        String origin = switch (trigger) {
            case SCHEDULE -> "agendada";
            case MANUAL -> "manual";
            case STARTUP -> "recuperacao na subida";
        };
        return taskName + ", " + origin;
    }

    public Map<String, String> values() {
        return values;
    }

    public String text(String name) {
        return TaskAttributeValues.normalize(values.get(name));
    }

    public String requireText(String name) {
        String v = text(name);
        if (v == null) {
            throw new TaskInputException("Atributo " + name + " é obrigatório.");
        }
        return v;
    }

    public Integer year(String name) {
        return TaskAttributeValues.asYear(values.get(name));
    }

    public Integer integer(String name) {
        return TaskAttributeValues.asInteger(values.get(name));
    }

    public LocalDate date(String name) {
        return TaskAttributeValues.asDate(values.get(name));
    }

    public boolean bool(String name, boolean whenEmpty) {
        Boolean v = TaskAttributeValues.asBoolean(values.get(name));
        return v == null ? whenEmpty : v;
    }

    public List<String> list(String name) {
        return TaskAttributeValues.asList(values.get(name));
    }

    /** Parada cooperativa (D8): chamada entre unidades de trabalho; com Stop pedido, encerra a rodada aqui. */
    public void checkStop() {
        if (stopRequested.getAsBoolean()) {
            throw new TaskStoppedException();
        }
    }

    public void summary(String summary) {
        this.summary = summary;
    }

    public String summary() {
        return summary;
    }

    /** Resultado para quem chamou de forma síncrona (a curadoria devolve a situação de cada linha). */
    public void result(Object result) {
        this.result = result;
    }

    public Object result() {
        return result;
    }
}
