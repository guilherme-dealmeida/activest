package com.primevest.adm.scheduler;

import com.primevest.adm.domain.fundamentals.FiiMonthlyImportService;
import com.primevest.adm.domain.fundamentals.FinancialStatementImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Balancos da CVM e informe mensal de FII do ano corrente e do anterior, uma vez
 * ao dia (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 6.2; informe desde a F7). Um nao impede o
 * outro. A CVM atualiza os arquivos de madrugada; so baixa o que mudou
 * ({@code If-Modified-Since}) e so grava documento ou mes novo ou reapresentado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CvmStatementsScheduler {

    private final FinancialStatementImportService service;
    private final FiiMonthlyImportService fiiMonthly;

    @Value("${primevest.cvm-statements.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${primevest.cvm-statements.cron:0 30 7 * * *}", zone = "America/Sao_Paulo")
    public void importRecent() {
        if (!enabled) {
            log.info("Balancos CVM: rotina desligada (primevest.cvm-statements.enabled=false)");
            return;
        }
        try {
            service.importRecent("07:30");
        } catch (RuntimeException e) {
            log.error("Balancos CVM (07:30) falhou: {}", e.getMessage(), e);
        }
        try {
            fiiMonthly.importRecent("07:30");
        } catch (RuntimeException e) {
            log.error("Informe FII (07:30) falhou: {}", e.getMessage(), e);
        }
    }
}
