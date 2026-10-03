package com.primevest.adm.scheduler;

import com.primevest.adm.domain.dividend.CvmPaymentDateService;
import com.primevest.adm.domain.dividend.ProventosCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proventos e eventos societarios da B3, uma vez ao dia (docs/planos/PLANO_BRAPI.md, F5).
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

    private final ProventosCatalogService proventosCatalogService;
    private final CvmPaymentDateService cvmPaymentDateService;

    @Value("${primevest.proventos.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${primevest.proventos.cron:0 0 20 * * *}", zone = "America/Sao_Paulo")
    public void syncProventos() {
        if (!enabled) {
            log.info("Proventos: rotina desligada (primevest.proventos.enabled=false)");
            return;
        }
        proventosCatalogService.syncAll("20:00");
        // data de pagamento que so o relatorio de proventos da CVM tem (docs/planos/PLANO_CARGA_INICIAL.md, 5.2):
        // ano corrente e anterior — o indice IPE so e baixado de novo se mudou
        int year = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
        cvmPaymentDateService.run(year - 1, year, "20:00");
    }
}
