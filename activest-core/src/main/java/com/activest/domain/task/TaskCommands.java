package com.activest.domain.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Os comandos entre os serviços (D29), dos dois lados: {@link #changed} avisa que as tarefas de um serviço mudaram (o
 * próprio serviço reagenda na hora; o outro, pelo Redis); {@link #runElsewhere} pede o Run de uma tarefa do outro
 * serviço; {@link #receive} trata o que chegou pelo Redis.
 */
@Slf4j
@Component
public class TaskCommands {

    private final ObjectProvider<TaskScheduleRegistry> registry;
    private final ObjectProvider<TaskCommandPublisher> publisher;
    private final TaskExecutor executor;
    private final String service;

    public TaskCommands(ObjectProvider<TaskScheduleRegistry> registry, ObjectProvider<TaskCommandPublisher> publisher,
                        TaskExecutor executor, @Value("${activest.tasks.service:}") String service) {
        this.registry = registry;
        this.publisher = publisher;
        this.executor = executor;
        this.service = service;
    }

    public boolean isLocal(TaskApi api) {
        return api.name().equals(service);
    }

    /** As tarefas de {@code api} mudaram (horário, ligado/desligado, nome, exclusão). */
    public void changed(TaskApi api) {
        if (isLocal(api)) {
            registry.ifAvailable(TaskScheduleRegistry::reload);
        } else {
            publisher.ifAvailable(p -> p.publish(TaskCommand.reload(api)));
        }
    }

    /** Run de uma tarefa do outro serviço: o comando vai pelo Redis; a rodada aparece no banco quando ele começar. */
    public void runElsewhere(TaskApi api, Long taskId, UUID requestedBy) {
        TaskCommandPublisher p = publisher.getIfAvailable();
        if (p == null) {
            throw new IllegalStateException("Sem mensagens entre os serviços: não dá para pedir o Run a " + api);
        }
        p.publish(TaskCommand.run(api, taskId, requestedBy));
    }

    /** Comando recebido pelo Redis. */
    public void receive(TaskCommand command) {
        if (command == null || command.service() == null || !isLocal(command.service())) {
            return;
        }
        switch (command.action()) {
            case RELOAD -> registry.ifAvailable(TaskScheduleRegistry::reload);
            case RUN -> {
                try {
                    executor.runManual(command.taskId(), command.requestedBy());
                } catch (RuntimeException e) {
                    log.warn("Tarefas: Run da tarefa {} pedido pelo outro serviço recusado: {}", command.taskId(),
                        e.getMessage());
                }
            }
        }
    }
}
