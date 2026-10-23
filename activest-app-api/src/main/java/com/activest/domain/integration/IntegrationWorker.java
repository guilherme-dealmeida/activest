package com.activest.domain.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Processa a fila de sincronizacoes (docs/planos/PLANO_CRIPTO_BINANCE.md, F5).
 *
 * <p>A fila e a propria {@code integration_runs}: a cada
 * {@code activest.integrations.worker-delay-ms} o worker pega as execucoes
 * QUEUED, uma de cada vez — o que tambem faz do limite de peso da Binance um
 * orcamento unico para todos os usuarios.</p>
 *
 * <p>Desligado no perfil de testes, como as demais rotinas: la o teste chama
 * {@link IntegrationService#processNext()} quando quer. Segue {@code activest.scheduling.enabled},
 * a menos que {@code activest.integrations.worker.enabled} diga outra coisa — a pilha
 * activest-teste liga so o worker: ele processa o que o usuario pediu, nao busca nada sozinho.</p>
 */
@Slf4j
@Component
@ConditionalOnExpression("${activest.integrations.worker.enabled:${activest.scheduling.enabled:true}}")
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
    public void onStartup() {
        service.recoverStale(LocalDateTime.now());
    }

    @Scheduled(fixedDelayString = "${activest.integrations.worker-delay-ms:5000}", initialDelay = 10_000)
    public void process() {
        try {
            while (service.processNext()) {
                // uma por vez, ate esvaziar
            }
        } catch (RuntimeException e) {
            log.error("Worker de integracoes: erro inesperado", e);
        }
    }
}
