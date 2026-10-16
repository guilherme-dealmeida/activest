package com.primevest.adm.scheduler;

import com.primevest.adm.domain.fixedincome.FixedIncomeIssuerImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Emissoras e distribuidoras de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 4.2): uma vez por mes (dia 1,
 * 05:30). O IF.data do BCB e trimestral e o cadastro da CVM e diario, mas o que muda para o usuario (empresa nova,
 * morta, renomeada) e lento. Banco novo nasce do seed (V55); a subida nao procura nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class FixedIncomeIssuerScheduler {

    private final FixedIncomeIssuerImportService service;

    /** Falha da rotina vira mensagem de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.primevest.adm.domain.notice.AdminNotices notices;

    @Scheduled(cron = "${primevest.fixed-income-issuers.cron:0 30 5 1 * *}", zone = "America/Sao_Paulo")
    public void monthly() {
        try {
            service.importAll("mensal");
        } catch (RuntimeException e) {
            log.error("Emissoras de renda fixa (mensal) falhou: {}", e.getMessage(), e);
            notices.routineFailed("Emissoras de renda fixa", e.getMessage());
        }
    }
}
