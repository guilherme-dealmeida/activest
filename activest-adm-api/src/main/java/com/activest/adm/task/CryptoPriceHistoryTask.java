package com.activest.adm.task;

import com.activest.adm.domain.pricehistory.CryptoHistoryService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
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
