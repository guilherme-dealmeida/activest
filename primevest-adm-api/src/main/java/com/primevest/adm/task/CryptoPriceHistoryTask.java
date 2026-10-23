package com.primevest.adm.task;

import com.primevest.adm.domain.pricehistory.CryptoHistoryService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Histórico de preço de cripto (CoinGecko e Binance) (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class CryptoPriceHistoryTask implements TaskHandler {

    private final CryptoHistoryService service;

    @Override
    public String type() {
        return "CRYPTO_PRICE_HISTORY";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.importAll(ctx.reason())));
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
