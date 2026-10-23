package com.activest.adm.task;

import com.activest.adm.domain.pricehistory.ClosingPriceService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Fechamento das criptomoedas [A2], com a recuperação na subida [A3] (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class CryptoClosingTask implements TaskHandler {

    private final ClosingPriceService service;

    @Override
    public String type() {
        return "CRYPTO_CLOSING";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.runCryptoClosing(ctx.reason())));
    }

    @Override
    public boolean needsRecovery() {
        return service.cryptoNeedsRecovery();
    }
}
