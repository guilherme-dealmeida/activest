package com.primevest.domain.task;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Datas e horas das tarefas no fuso de São Paulo — o mesmo dos horários (cron) das rotinas. */
public final class TaskTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private TaskTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
