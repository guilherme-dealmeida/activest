package com.primevest.scheduler;

import com.primevest.domain.notification.DailyOpeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina de abertura do dia — [B7] (docs/planos/PLANO_NOTIFICACOES.md, seção 7): meia-noite e na subida da app-api
 * ("o primeiro horário do dia com o sistema no ar"). Idempotente pelas chaves das mensagens: subir duas vezes no dia
 * não duplica nada, e não há coluna de controle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DailyOpeningScheduler {

    private final DailyOpeningService service;

    @Scheduled(cron = "${primevest.abertura.cron:0 0 0 * * *}", zone = "America/Sao_Paulo")
    public void midnight() {
        service.run("00:00");
    }

    /** Na subida: fora da thread de inicialização — a liquidação e as conferências podem levar alguns segundos. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread.ofVirtual().name("abertura-do-dia").start(() -> {
            try {
                service.run("subida");
            } catch (RuntimeException e) {
                log.error("Abertura do dia na subida falhou: {}", e.getMessage(), e);
            }
        });
    }
}
