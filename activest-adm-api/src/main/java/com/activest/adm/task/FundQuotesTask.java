package com.activest.adm.task;

import com.activest.adm.domain.fund.FundCatalogService;
import com.activest.adm.integration.PublicFileCache;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Fundos de investimento da CVM (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, 11.4): cadastro e cota diária. Sem
 * atributo, o mês corrente e os 12 anteriores (D9); com {@code FROM_MONTH}, rebaixa a partir dele. <b>No máximo uma
 * rodada automática por dia</b> (D8): agendada às 07:40, a subida só substitui a agendada perdida, o Run manual roda
 * sempre.
 */
@Component
@RequiredArgsConstructor
public class FundQuotesTask implements TaskHandler {

    private final FundCatalogService service;

    @Override
    public String type() {
        return "FUND_QUOTES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        LocalDate from = ctx.date("FROM_MONTH");
        if (from != null && (from.getYear() < 2010 || from.isAfter(LocalDate.now()))) {
            throw new TaskInputException("Mês inicial inválido: " + from + " (de 2010 até hoje).");
        }
        FundCatalogService.Result result = service.runDaily(from == null ? null : YearMonth.from(from), ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("Os fundos já estavam sendo carregados por outro caminho.");
        }
        ctx.summary(result.toString());
        if (result.sourceProblem() != null) {
            // Como no Tesouro (PLANO_FONTE_TESOURO.md, D2): a CVM recusou e só a cópia em disco foi lida — a rodada
            // termina em FAILED, com o resumo gravado; o aviso FONTE_INDISPONIVEL já foi aberto pelo serviço.
            throw new PublicFileCache.UnavailableException("Informe diário dos fundos: " + result.sourceProblem(), null);
        }
    }

    @Override
    public boolean needsRecovery() {
        return service.behind();
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
