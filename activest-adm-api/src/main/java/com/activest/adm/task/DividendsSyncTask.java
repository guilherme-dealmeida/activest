package com.activest.adm.task;

import com.activest.adm.domain.dividend.CvmPaymentDateService;
import com.activest.adm.domain.dividend.DividendCatalogService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proventos e eventos societários [A4]: B3 e FNET por emissor e, com {@code WITH_CVM_PAYMENT_DATES}, as datas de
 * pagamento da CVM do ano corrente e do anterior (docs/planos/PLANO_TAREFAS.md, T2).
 */
@Component
@RequiredArgsConstructor
public class DividendsSyncTask implements TaskHandler {

    private final DividendCatalogService dividends;
    private final CvmPaymentDateService cvmPaymentDates;

    @Override
    public String type() {
        return "DIVIDENDS_SYNC";
    }

    @Override
    public void run(TaskRunContext ctx) {
        var result = dividends.syncAll(ctx.reason(), ctx.bool("FNET_REREAD_ALL", false));
        if (result == null) {
            throw new TaskSkippedException("Já havia uma rodada completa de proventos em andamento por outro caminho "
                + "(ex.: a carga inicial ou a situação do FNET).");
        }
        Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("proventos", result);
        if (ctx.bool("WITH_CVM_PAYMENT_DATES", true)) {
            int year = TaskYears.currentYear();
            parts.put("datas CVM", cvmPaymentDates.run(year - 1, year, ctx.reason()));
        }
        ctx.summary(TaskSummaries.join(parts));
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
