package com.primevest.adm.task;

import com.primevest.adm.domain.fixedincome.FixedIncomeIssuerImportService;
import com.primevest.adm.domain.fixedincome.FixedIncomeLogoService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskStoppedException;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emissoras e distribuidoras de renda fixa e, em seguida, os logos delas (docs/planos/PLANO_TAREFAS.md, T2). Uma parte
 * que falha não impede a outra; a rodada falha no fim.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixedIncomeIssuersTask implements TaskHandler {

    private final FixedIncomeIssuerImportService issuers;
    private final FixedIncomeLogoService logos;

    @Override
    public String type() {
        return "FIXED_INCOME_ISSUERS";
    }

    @Override
    public void run(TaskRunContext ctx) {
        Map<String, Object> parts = new LinkedHashMap<>();
        RuntimeException failure = null;
        if (ctx.bool("WITH_ISSUERS", true)) {
            try {
                parts.put("emissoras", issuers.importAll(ctx.reason()));
            } catch (TaskStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Emissoras de renda fixa falharam: {}", e.getMessage(), e);
                failure = e;
            }
        }
        if (ctx.bool("WITH_LOGOS", true)) {
            try {
                parts.put("logos", logos.sync(!ctx.bool("LOGOS_REFRESH_ALL", false), ctx.reason()));
            } catch (TaskStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Logos de renda fixa falharam: {}", e.getMessage(), e);
                failure = failure == null ? e : failure;
            }
        }
        ctx.summary(TaskSummaries.join(parts));
        if (failure != null) {
            throw failure;
        }
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
