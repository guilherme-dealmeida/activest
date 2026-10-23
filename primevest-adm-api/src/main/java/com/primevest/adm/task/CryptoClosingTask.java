package com.primevest.adm.task;

import com.primevest.adm.domain.pricehistory.ClosingPriceService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
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
