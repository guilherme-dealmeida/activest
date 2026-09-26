package com.primevest.scheduler;

import com.primevest.domain.integration.IntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincronizacao diaria das conexoes com corretoras (PLANO_CRIPTO_BINANCE.md,
 * F5). So enfileira; quem busca e o IntegrationWorker. A execucao agendada se
 * confirma sozinha quando nao ha nada a decidir e a conexao ja teve uma
 * importacao confirmada pelo usuario.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IntegrationSyncScheduler {

    private final IntegrationService integrationService;

    @Scheduled(cron = "${primevest.integrations.sync-cron:0 30 6 * * *}", zone = "America/Sao_Paulo")
    public void sincronizar() {
        int n = integrationService.enqueueScheduled();
        log.info("Integracoes: {} conexao(oes) na fila da sincronizacao diaria", n);
    }
}
