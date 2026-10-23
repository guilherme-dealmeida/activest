package com.activest.domain.task;

/** Publica um {@link TaskCommand} no Redis. Implementado em cada serviço; sem mensagens, só no log. */
public interface TaskCommandPublisher {

    void publish(TaskCommand command);
}
