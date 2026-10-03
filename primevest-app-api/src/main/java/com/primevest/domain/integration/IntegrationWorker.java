package com.primevest.domain.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Processa a fila de sincronizacoes (docs/planos/PLANO_CRIPTO_BINANCE.md, F5).
 *
 * <p>A fila e a propria {@code integration_runs}: a cada
 * {@code primevest.integrations.worker-delay-ms} o worker pega as execucoes
 * QUEUED, uma de cada vez — o que tambem faz do limite de peso da Binance um
 * orcamento unico para todos os usuarios.</p>
 *
 * <p>Desligado no perfil de testes, como as demais rotinas: la o teste chama
 * {@link IntegrationService#processNext()} quando quer.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IntegrationWorker {

    private final IntegrationService service;

    public IntegrationWorker(IntegrationService service) {
        this.service = service;
    }

    /**
     * Na subida, o que estava RUNNING morreu com a instancia anterior: vira
     * FAILED na hora, para a conexao nao esperar o prazo de
     * {@code stale-minutes}. Vale enquanto houver uma instancia so da API.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        service.recuperarPerdidas(LocalDateTime.now());
    }

    @Scheduled(fixedDelayString = "${primevest.integrations.worker-delay-ms:5000}", initialDelay = 10_000)
    public void processar() {
        try {
            while (service.processNext()) {
                // uma por vez, ate esvaziar
            }
        } catch (RuntimeException e) {
            log.error("Worker de integracoes: erro inesperado", e);
        }
    }
}
