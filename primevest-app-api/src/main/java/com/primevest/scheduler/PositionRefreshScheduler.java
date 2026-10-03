package com.primevest.scheduler;

import com.primevest.domain.portfolio.PositionRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de seguranca da carteira (docs/planos/PLANO_ADM_API.md, 10.3): uma vez por dia, depois do
 * fechamento (19:00) e dos proventos (20:00) da adm-api, recalcula proventos e valorizacao de
 * todas as posicoes. Cobre o "catalogo mudou" que se perdeu (Redis ou app-api fora do ar).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PositionRefreshScheduler {

    private final PositionRefreshService positionRefreshService;

    @Scheduled(cron = "${primevest.positions.refresh-cron:0 0 21 * * *}", zone = "America/Sao_Paulo")
    public void refreshAll() {
        positionRefreshService.refreshAll("21:00");
    }
}
