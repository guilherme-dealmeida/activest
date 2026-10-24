package com.activest.adm.task;

import com.activest.adm.domain.marketindex.MarketIndexImportService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Índices de mercado [A9]: sem códigos e sem período, a rodada incremental; com algum deles, a carga do período —
 * primeiro ano vazio com último informado = desde o início de cada índice (docs/planos/PLANO_TAREFAS.md, T2).
 */
@Component
@RequiredArgsConstructor
public class MarketIndicesTask implements TaskHandler {

    private final MarketIndexImportService service;

    @Override
    public String type() {
        return "MARKET_INDICES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        List<String> codes = ctx.list("INDEX_CODES").stream().map(String::toUpperCase).toList();
        Integer from = ctx.year("FROM_YEAR");
        Object result;
        if (from == null && ctx.year("TO_YEAR") == null && codes.isEmpty()) {
            result = service.importRecent(ctx.reason());
        } else {
            if (!codes.isEmpty()) {
                try {
                    service.validateCodes(codes);
                } catch (IllegalArgumentException e) {
                    throw new TaskInputException(e.getMessage());
                }
            }
            int to = TaskYears.to(ctx.year("TO_YEAR"));
            if ((from != null && (from < 1986 || to < from)) || to < 1986) {
                throw new TaskInputException("Período inválido: " + from + " a " + to + ".");
            }
            result = service.importYears(codes.isEmpty() ? null : codes, from, to, ctx.reason());
        }
        if (result == null) {
            throw new TaskSkippedException("Os índices já estavam sendo carregados por outro caminho.");
        }
        ctx.summary(TaskSummaries.of(result));
    }

    @Override
    public boolean needsRecovery() {
        return !service.stale().isEmpty();
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
