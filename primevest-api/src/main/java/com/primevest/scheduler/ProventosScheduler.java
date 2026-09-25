package com.primevest.scheduler;

import com.primevest.domain.dividend.ProventosSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proventos e eventos societarios da B3, uma vez ao dia (PLANO_BRAPI.md, F5).
 *
 * <p>Substitui o DividendSyncScheduler, que pedia dividendos a BRAPI — recurso
 * pago (403 no plano gratuito), com range fora do plano (400) e um DTO que nao
 * batia com a resposta.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ProventosScheduler {

    private final ProventosSyncService proventosSyncService;

    @Value("${primevest.proventos.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${primevest.proventos.cron:0 0 20 * * *}", zone = "America/Sao_Paulo")
    public void syncProventos() {
        if (!enabled) {
            log.info("Proventos: rotina desligada (primevest.proventos.enabled=false)");
            return;
        }
        proventosSyncService.syncAll("20:00");
    }
}
