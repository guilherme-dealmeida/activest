package com.activest.adm.task;

import com.activest.adm.domain.foreign.ForeignCorporateEventsService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Carga dos proventos e desdobramentos dos ativos internacionais, mês a mês (docs/planos/PLANO_PROVENTOS_INTERNACIONAIS.md,
 * D2): de {@code FROM_DATE} (padrão 2010-01-01) a {@code TO_DATE} (vazio = 90 dias à frente). Mês encerrado e guardado
 * em disco não volta à rede. Só Run.
 */
@Component
@RequiredArgsConstructor
public class ForeignDividendsHistoryTask implements TaskHandler {

    static final LocalDate FIRST_DAY = LocalDate.of(2010, 1, 1);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final ForeignCorporateEventsService service;

    @Override
    public String type() {
        return "FOREIGN_DIVIDENDS_HISTORY";
    }

    @Override
    public void run(TaskRunContext ctx) {
        LocalDate today = LocalDate.now(NEW_YORK);
        LocalDate from = ctx.date("FROM_DATE") != null ? ctx.date("FROM_DATE") : FIRST_DAY;
        LocalDate to = ctx.date("TO_DATE") != null ? ctx.date("TO_DATE") : today.plusDays(90);
        if (from.isBefore(FIRST_DAY) || to.isBefore(from) || to.isAfter(today.plusYears(1))) {
            throw new TaskInputException("Período inválido: " + from + " a " + to + " (de " + FIRST_DAY
                    + " até um ano à frente).");
        }
        ForeignCorporateEventsService.Result r = service.importMonths(YearMonth.from(from), YearMonth.from(to), false,
                ctx.reason());
        if (r == null) {
            throw new TaskSkippedException("Os proventos internacionais já estavam sendo carregados por outra rodada.");
        }
        ctx.summary(TaskSummaries.of(r));
    }

    @Override
    public boolean needsRecovery() {
        return false;
    }

    @Override
    public boolean stoppable() {
        return true;
    }
}
