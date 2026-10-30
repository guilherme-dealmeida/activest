package com.activest.adm.task;

import com.activest.adm.domain.foreign.ForeignDelistedService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Deslistados dos EUA desde 2010 (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C6, 11.4 e F5): Form 25 da SEC → ticker,
 * tipo e categoria → prévia da planilha; com {@code WRITE=true}, cadastra os papéis como inativos com a data de saída. Só
 * Run manual (e o {@code bootstrap}); os Form 25 novos entram pela {@code FOREIGN_CATALOG}.
 */
@Component
@RequiredArgsConstructor
public class ForeignDelistedTask implements TaskHandler {

    private final ForeignDelistedService service;

    @Override
    public String type() {
        return "FOREIGN_DELISTED";
    }

    @Override
    public void run(TaskRunContext ctx) {
        Integer from = ctx.year("FROM_YEAR");
        Integer to = ctx.year("TO_YEAR");
        int now = LocalDate.now().getYear();
        if ((from != null && (from < 2010 || from > now)) || (to != null && (to < 2010 || to > now))
                || (from != null && to != null && from > to)) {
            throw new TaskInputException("Anos inválidos: de " + from + " a " + to + " (de 2010 até hoje).");
        }
        ForeignDelistedService.Result result = service.run(from, to, ctx.bool("WRITE", false), ctx.reason(),
                ctx::checkStop);
        ctx.summary(result.toString());
    }

    @Override
    public boolean stoppable() {
        return true;
    }

    @Override
    public boolean needsRecovery() {
        return false;
    }
}
