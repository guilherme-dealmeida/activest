package com.activest.adm.task;

import com.activest.adm.domain.asset.AssetLogoService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Logos dos ativos [A7] (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class AssetLogosTask implements TaskHandler {

    private final AssetLogoService service;

    @Override
    public String type() {
        return "ASSET_LOGOS";
    }

    @Override
    public void run(TaskRunContext ctx) {
        ctx.summary(TaskSummaries.of(service.sync(ctx.bool("ONLY_MISSING", true), ctx.reason())));
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
