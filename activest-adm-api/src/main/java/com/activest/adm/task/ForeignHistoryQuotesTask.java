package com.activest.adm.task;

import com.activest.adm.domain.pricehistory.ForeignDailyQuotesService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Carga retroativa do preço dos ativos internacionais pelo Massive grouped daily
 * (docs/planos/PLANO_PRECOS_HISTORICOS_INTERNACIONAIS.md): um pedido por pregão (a 5 por minuto), do {@code FROM_DATE}
 * (vazio = 2 anos atrás, o limite do plano gratuito) ao {@code TO_DATE} (vazio = ontem). Dia que a fonte não entrega
 * no plano é contado e pulado; dia já baixado é relido do arquivo em {@code carga/data/massive/grouped}. Só Run (e a
 * carga inicial); a rotina diária é a {@code FOREIGN_DAILY_QUOTES}.
 */
@Component
@RequiredArgsConstructor
public class ForeignHistoryQuotesTask implements TaskHandler {

    /** Início da série (como a B3); antes disso nem a fonte paga entra no catálogo. */
    static final LocalDate FIRST_DAY = LocalDate.of(2010, 1, 1);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final ForeignDailyQuotesService service;

    @Override
    public String type() {
        return "FOREIGN_HISTORY_QUOTES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        LocalDate today = LocalDate.now(NEW_YORK);
        LocalDate from = ctx.date("FROM_DATE") != null ? ctx.date("FROM_DATE") : today.minusYears(2);
        LocalDate to = ctx.date("TO_DATE") != null ? ctx.date("TO_DATE") : today.minusDays(1);
        if (from.isBefore(FIRST_DAY) || to.isBefore(from) || to.isAfter(today)) {
            throw new TaskInputException("Período inválido: " + from + " a " + to + " (de " + FIRST_DAY
                    + " até ontem).");
        }
        ForeignDailyQuotesService.Result result = service.importHistory(from, to, ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("O preço dos internacionais já estava sendo carregado por outra rodada.");
        }
        ctx.summary(TaskSummaries.of(result));
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
