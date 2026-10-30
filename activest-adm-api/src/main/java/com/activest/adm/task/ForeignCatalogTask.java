package com.activest.adm.task;

import com.activest.adm.domain.foreign.ForeignCatalogService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Catálogo vivo dos EUA (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, 11.4 e F4): Stock, REIT e ETF internacional da
 * lista da bolsa, com tipo pelo OpenFIGI, categoria e saída com prova. {@code WRITE=false} (padrão) só gera a prévia
 * para a planilha de revisão (D12); {@code WRITE=true} grava o catálogo.
 */
@Component
@RequiredArgsConstructor
public class ForeignCatalogTask implements TaskHandler {

    private final ForeignCatalogService service;

    @Override
    public String type() {
        return "FOREIGN_CATALOG";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ForeignCatalogService.Result result = service.run(ctx.bool("WRITE", false), ctx.reason(), ctx::checkStop);
        ctx.summary(result.toString());
    }

    @Override
    public boolean stoppable() {
        return true;
    }
}
