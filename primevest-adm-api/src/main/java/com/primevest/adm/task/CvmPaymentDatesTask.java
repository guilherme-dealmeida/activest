package com.primevest.adm.task;

import com.primevest.adm.domain.dividend.CvmPaymentDateService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSkippedException;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Datas de pagamento pelos relatórios de proventos da CVM, de {@code FROM_YEAR} a {@code TO_YEAR} (T2). */
@Component
@RequiredArgsConstructor
public class CvmPaymentDatesTask implements TaskHandler {

    private final CvmPaymentDateService service;

    @Override
    public String type() {
        return "CVM_PAYMENT_DATES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        int from = ctx.year("FROM_YEAR");
        int to = TaskYears.to(ctx.year("TO_YEAR"));
        TaskYears.check("Datas de pagamento CVM", from, to, CvmPaymentDateService.FIRST_YEAR);
        var result = service.run(from, to, ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("As datas de pagamento da CVM já estavam sendo aplicadas por outro "
                + "caminho (ex.: a rodada de proventos).");
        }
        ctx.summary(TaskSummaries.of(result));
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
