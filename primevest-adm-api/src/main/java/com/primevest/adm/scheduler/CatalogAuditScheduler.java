package com.primevest.adm.scheduler;

import com.primevest.adm.domain.asset.CatalogAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Conferencia semanal do catalogo (docs/planos/PLANO_BRAPI.md, B23): segunda, 07:00. Sob demanda: /api/v1/admin/catalog-audit. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogAuditScheduler {

    private final CatalogAuditService catalogAuditService;

    @Scheduled(cron = "${primevest.catalog-audit.cron:0 0 7 * * MON}", zone = "America/Sao_Paulo")
    public void weeklyAudit() {
        catalogAuditService.run("semanal");
    }
}
