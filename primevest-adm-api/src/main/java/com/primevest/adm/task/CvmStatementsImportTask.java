package com.primevest.adm.task;

import com.primevest.adm.domain.fundamentals.FiiMonthlyImportService;
import com.primevest.adm.domain.fundamentals.FinancialStatementImportService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskStoppedException;
import com.primevest.domain.task.TaskSkippedException;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Balanços da CVM e informe mensal de FII [A5] (D21: atributos por parte). Sem primeiro ano, a parte roda o
 * incremental (ano corrente e anterior). Uma parte que falha não impede a outra; a rodada falha no fim
 * (docs/planos/PLANO_TAREFAS.md, T2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CvmStatementsImportTask implements TaskHandler {

    private final FinancialStatementImportService statements;
    private final FiiMonthlyImportService fiiMonthly;

    @Override
    public String type() {
        return "CVM_STATEMENTS_IMPORT";
    }

    @Override
    public void run(TaskRunContext ctx) {
        boolean force = ctx.bool("FORCE", false);
        Map<String, Object> parts = new LinkedHashMap<>();
        RuntimeException failure = null;
        boolean skipped = false;

        if (ctx.bool("WITH_STATEMENTS", true)) {
            try {
                Integer from = ctx.year("STATEMENTS_FROM_YEAR");
                Object r;
                if (from == null) {
                    r = statements.importRecent(ctx.reason());
                } else {
                    int to = TaskYears.to(ctx.year("STATEMENTS_TO_YEAR"));
                    TaskYears.check("Balanços", from, to, FinancialStatementImportService.FIRST_YEAR);
                    r = statements.importYears(from, to, force, ctx.reason());
                }
                skipped |= r == null;
                parts.put("balancos", r);
            } catch (TaskStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Balancos CVM falharam: {}", e.getMessage(), e);
                failure = e;
            }
        }
        if (ctx.bool("WITH_FII_MONTHLY", true)) {
            try {
                Integer from = ctx.year("FII_FROM_YEAR");
                Object r;
                if (from == null) {
                    r = fiiMonthly.importRecent(ctx.reason());
                } else {
                    int to = TaskYears.to(ctx.year("FII_TO_YEAR"));
                    TaskYears.check("Informe de FII", from, to, FiiMonthlyImportService.FIRST_YEAR);
                    r = fiiMonthly.importYears(from, to, force, ctx.reason());
                }
                skipped |= r == null;
                parts.put("informe FII", r);
            } catch (TaskStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Informe FII falhou: {}", e.getMessage(), e);
                failure = failure == null ? e : failure;
            }
        }
        ctx.summary(TaskSummaries.join(parts));
        if (failure != null) {
            throw failure;
        }
        if (skipped && parts.values().stream().allMatch(v -> v == null)) {
            throw new TaskSkippedException("A carga da CVM já estava rodando por outro caminho.");
        }
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
