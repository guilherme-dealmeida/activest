package com.primevest.domain.task;

import com.primevest.exception.BusinessException;

/** Atributo obrigatório vazio ou valor que o tipo recusa: 422 no pedido manual, {@code FAILED} na agendada. */
public class TaskInputException extends BusinessException {

    public TaskInputException(String message) {
        super(message);
    }
}
