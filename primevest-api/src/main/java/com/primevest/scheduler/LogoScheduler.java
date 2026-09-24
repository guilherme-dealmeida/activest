package com.primevest.scheduler;

import com.primevest.domain.asset.AssetLogoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Logos (PLANO_BRAPI.md, B25): todos uma vez por mes (dia 1, 06:00) e, na
 * subida da API, os ativos novos — os que nunca foram procurados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class LogoScheduler {

    private final AssetLogoService assetLogoService;

    @Scheduled(cron = "${primevest.logos.cron:0 0 6 1 * *}", zone = "America/Sao_Paulo")
    public void monthly() {
        assetLogoService.sync(false, "mensal");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void newAssetsOnStartup() {
        Thread.ofVirtual().name("logos-ativos-novos").start(() -> {
            try {
                assetLogoService.sync(true, "ativos novos na subida");
            } catch (RuntimeException e) {
                log.error("Logos na subida falharam: {}", e.getMessage(), e);
            }
        });
    }
}
