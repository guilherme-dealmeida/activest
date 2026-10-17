package com.primevest.adm.scheduler;

import com.primevest.adm.domain.pricehistory.CryptoHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Historico de preco de cripto (docs/planos/PLANO_RENTABILIDADE.md, 3.3): todo dia as 19:30, depois do fechamento de
 * cripto das 19:00. Completa os dias que faltam (moeda nova em carteira, dia sem fechamento) e soma a janela da
 * CoinGecko ao arquivo de carga/data antes que ela ande.
 *
 * <p>Horario em {@code primevest.cripto-historico.cron}; desligada com {@code primevest.cripto-historico.enabled=false}
 * ou com as demais rotinas ({@code primevest.scheduling.enabled}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CryptoHistoryScheduler {

    /** Falha da rotina vira mensagem de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.primevest.adm.domain.notice.AdminNotices notices;

    private final CryptoHistoryService service;
    private final boolean enabled;

    public CryptoHistoryScheduler(CryptoHistoryService service,
                                  @Value("${primevest.cripto-historico.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${primevest.cripto-historico.cron:0 30 19 * * *}", zone = "America/Sao_Paulo")
    public void daily() {
        if (!enabled) {
            return;
        }
        try {
            service.importAll("19:30");
        } catch (RuntimeException e) {
            log.error("Historico de cripto (19:30) falhou: {}", e.getMessage(), e);
            notices.routineFailed("Histórico de cripto (19:30)", e.getMessage());
        }
    }
}
