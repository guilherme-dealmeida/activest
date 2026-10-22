package com.primevest.adm.task;

import com.primevest.domain.task.TaskInputException;
import com.primevest.domain.task.TaskTime;

import java.time.LocalDate;

/** Período em anos dos atributos {@code *_FROM_YEAR}/{@code *_TO_YEAR}: fim vazio = ano corrente; confere os limites. */
final class TaskYears {

    private TaskYears() {
    }

    static int currentYear() {
        return LocalDate.now(TaskTime.ZONE).getYear();
    }

    static int to(Integer to) {
        return to != null ? to : currentYear();
    }

    static void check(String what, int from, int to, int min) {
        if (from < min || to < from) {
            throw new TaskInputException(what + ": período inválido (" + from + " a " + to + "; primeiro ano "
                + min + " ou depois).");
        }
    }
}
