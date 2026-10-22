package com.primevest.adm.task;

import com.primevest.adm.domain.asset.CatalogAuditService;
import com.primevest.adm.domain.marketindex.MarketIndexImportService;

import java.util.LinkedHashMap;
import java.util.Map;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Conferência do catálogo e dos índices atrasados [A6] (docs/planos/PLANO_TAREFAS.md, T2). */
@Component
@RequiredArgsConstructor
public class CatalogAuditTask implements TaskHandler {

    private final CatalogAuditService catalogAudit;
    private final MarketIndexImportService indices;

    @Override
    public String type() {
        return "CATALOG_AUDIT";
    }

    @Override
    public void run(TaskRunContext ctx) {
        Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("catalogo", catalogAudit.run(ctx.reason()));
        parts.put("indices atrasados", indices.audit(ctx.reason()));
        ctx.summary(TaskSummaries.join(parts));
    }
}
