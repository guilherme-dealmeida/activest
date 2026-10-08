package com.primevest.adm.scheduler;

import com.primevest.adm.domain.pricehistory.TreasuryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Preco oficial do Tesouro Direto (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R1; D3): todo dia as 08:00, depois
 * de o Tesouro publicar o arquivo com o D-1 (~07:20). Na subida, se o ultimo PU gravado estiver atrasado, a rodada
 * roda na hora, em segundo plano.
 *
 * <p>Horario em {@code primevest.tesouro.cron}; desligada com {@code primevest.tesouro.enabled=false} ou com as
 * demais rotinas ({@code primevest.scheduling.enabled}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class TreasuryPriceScheduler {

    private final TreasuryPriceService service;
    private final boolean enabled;

    public TreasuryPriceScheduler(TreasuryPriceService service,
                                  @Value("${primevest.tesouro.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${primevest.tesouro.cron:0 0 8 * * *}", zone = "America/Sao_Paulo")
    public void daily() {
        if (enabled) {
            try {
                service.importRecent("08:00");
            } catch (RuntimeException e) {
                log.error("Tesouro (08:00) falhou: {}", e.getMessage(), e);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!enabled) {
            return;
        }
        Thread.ofVirtual().name("tesouro-recuperacao").start(() -> {
            try {
                if (service.needsRecovery()) {
                    service.importRecent("recuperacao na subida");
                } else {
                    log.info("Tesouro em dia: nada a recuperar na subida");
                }
            } catch (RuntimeException e) {
                log.error("Recuperacao do preco do Tesouro falhou: {}", e.getMessage(), e);
            }
        });
    }
}
