package com.activest.adm.task;

import com.activest.adm.domain.pricehistory.ForeignDailyQuotesService;
import com.activest.adm.integration.foreign.ForeignQuotesProvider;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Preço diário dos ativos internacionais vivos pelo Massive grouped daily (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md).
 * Sem período: do pregão seguinte ao último gravado até ontem em Nova York (D7). Com {@code FROM_DATE} (e
 * {@code TO_DATE}): o período, até {@value ForeignDailyQuotesService#MAX_DAYS} dias (I6). Agendada às 03:00 de terça a
 * sábado, com recuperação na subida e no máximo uma rodada automática por dia (D4).
 */
@Component
@RequiredArgsConstructor
public class ForeignDailyQuotesTask implements TaskHandler {

    private final ForeignDailyQuotesService service;

    @Override
    public String type() {
        return "FOREIGN_DAILY_QUOTES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        LocalDate from = ctx.date("FROM_DATE");
        LocalDate to = ctx.date("TO_DATE");
        ForeignDailyQuotesService.Result result;
        if (from == null) {
            if (to != null) {
                throw new TaskInputException("TO_DATE sem FROM_DATE: informe o primeiro pregão.");
            }
            result = service.importRecent(ctx.reason());
        } else {
            LocalDate end = to != null ? to : from;
            if (end.isBefore(from) || end.isAfter(from.plusDays(ForeignDailyQuotesService.MAX_DAYS - 1L))) {
                throw new TaskInputException("Período inválido: " + from + " a " + end + " (até "
                        + ForeignDailyQuotesService.MAX_DAYS + " dias).");
            }
            if (end.isAfter(LocalDate.now())) {
                throw new TaskInputException("Período inválido: " + end + " é depois de hoje.");
            }
            try {
                result = service.importDays(from, end, ctx.reason());
            } catch (ForeignQuotesProvider.OutOfRangeException e) {
                throw new TaskInputException(e.getMessage());
            }
        }
        if (result == null) {
            throw new TaskSkippedException("O preço dos internacionais já estava sendo carregado por outra rodada.");
        }
        ctx.summary(TaskSummaries.of(result));
    }

    @Override
    public boolean needsRecovery() {
        return service.stale();
    }

    @Override
    public boolean oncePerDay() {
        return true;
    }

    @Override
    public boolean stoppable() {
        return true;
    }
}
