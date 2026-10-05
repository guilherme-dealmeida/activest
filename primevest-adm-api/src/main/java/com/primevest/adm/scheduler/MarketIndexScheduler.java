package com.primevest.adm.scheduler;

import com.primevest.adm.domain.marketindex.MarketIndexImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina dos indices de mercado (docs/planos/PLANO_INDICES_MERCADO.md, 4.2; D5): todo dia as 10:00, depois de o
 * SGS publicar o CDI e a Selic do dia anterior (IPCA/INPC saem por volta do dia 10, as 9h). Incremental, com a
 * janela de revisao (D10).
 *
 * <p><b>Recuperacao:</b> a API roda na maquina do usuario e pode ficar dias desligada. Na subida, se algum indice
 * estiver atrasado (ou vazio), a rodada roda na hora, em segundo plano.</p>
 *
 * <p>Horario em {@code primevest.indices.cron}; desligada com {@code primevest.indices.enabled=false} ou com as
 * demais rotinas ({@code primevest.scheduling.enabled}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MarketIndexScheduler {

    private final MarketIndexImportService service;
    private final boolean enabled;

    public MarketIndexScheduler(MarketIndexImportService service,
                                @org.springframework.beans.factory.annotation.Value("${primevest.indices.enabled:true}")
                                boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${primevest.indices.cron:0 0 10 * * *}", zone = "America/Sao_Paulo")
    public void daily() {
        if (enabled) {
            service.importRecent("10:00");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!enabled) {
            return;
        }
        Thread.ofVirtual().name("indices-recuperacao").start(() -> {
            try {
                if (service.stale().isEmpty()) {
                    log.info("Indices em dia: nada a recuperar na subida");
                } else {
                    service.importRecent("recuperacao na subida");
                }
            } catch (RuntimeException e) {
                log.error("Recuperacao dos indices falhou: {}", e.getMessage(), e);
            }
        });
    }
}
