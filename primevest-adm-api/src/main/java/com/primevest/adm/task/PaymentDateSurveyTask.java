package com.primevest.adm.task;

import com.primevest.adm.domain.dividend.PaymentDateSurveyService;
import com.primevest.domain.task.TaskSkippedException;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Datas de pagamento do levantamento (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class PaymentDateSurveyTask implements TaskHandler {

    private final PaymentDateSurveyService service;

    @Override
    public String type() {
        return "PAYMENT_DATE_SURVEY";
    }

    @Override
    public void run(TaskRunContext ctx) {
        var result = service.run(ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("As datas do levantamento já estavam sendo aplicadas por outro caminho.");
        }
        ctx.summary(TaskSummaries.of(result));
    }
}
