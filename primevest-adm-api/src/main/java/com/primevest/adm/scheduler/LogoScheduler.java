package com.primevest.adm.scheduler;

import com.primevest.adm.domain.asset.AssetLogoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Logos (PLANO_BRAPI.md, B25; PLANO_EMPRESAS_BALANCOS.md, F10): uma vez por mes (dia 1,
 * 06:00), so os ativos sem arquivo em {@code data/logos}. A subida da API nao procura nada;
 * o ativo novo que entra numa carteira e procurado no pedido da carteira
 * ({@code AdmMessagingConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class LogoScheduler {

    private final AssetLogoService assetLogoService;

    @Scheduled(cron = "${primevest.logos.cron:0 0 6 1 * *}", zone = "America/Sao_Paulo")
    public void monthly() {
        assetLogoService.sync(true, "mensal, ativos sem logo");
    }
}
