package com.activest.adm.task;

import com.activest.adm.domain.dividend.PaymentDateSurveyService;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
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
