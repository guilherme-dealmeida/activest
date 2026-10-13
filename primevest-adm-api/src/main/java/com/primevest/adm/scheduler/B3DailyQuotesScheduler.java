package com.primevest.adm.scheduler;

import com.primevest.adm.domain.pricehistory.HistoricalQuotesImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cotacoes do catalogo inteiro pelo COTAHIST diario da B3 (docs/planos/PLANO_PRECO_TETO.md, 4.5 e D6): o
 * fechamento da BRAPI so cobre a carteira, e a cota gratuita nao cobre as ~800 acoes e FIIs. Um arquivo por
 * pregao (~0,5 MB), sem cota.
 *
 * <p>Roda a noite (o arquivo sai depois do fechamento) e de novo de manha, para o pregao que ainda nao tinha
 * saido; cada rodada completa todos os pregoes que faltam desde o ultimo gravado. Na subida, se estiver
 * atrasado, roda em segundo plano (a API pode ficar dias desligada).</p>
 *
 * <p>Horario em {@code primevest.cotahist-diario.cron}; desligada com {@code primevest.cotahist-diario.enabled=false}
 * ou com as demais rotinas ({@code primevest.scheduling.enabled}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class B3DailyQuotesScheduler {

    /** Falha da rotina vira mensagem de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.primevest.adm.domain.notice.AdminNotices notices;

    private final HistoricalQuotesImportService service;
    private final boolean enabled;

    public B3DailyQuotesScheduler(HistoricalQuotesImportService service,
                                  @Value("${primevest.cotahist-diario.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${primevest.cotahist-diario.cron:0 30 8,21 * * *}", zone = "America/Sao_Paulo")
    public void daily() {
        if (!enabled) {
            return;
        }
        try {
            service.importRecent("rotina diaria");
        } catch (RuntimeException e) {
            log.error("Cotacoes diarias da B3 falharam: {}", e.getMessage(), e);
            notices.routineFailed("Cotações diárias da B3", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!enabled) {
            return;
        }
        Thread.ofVirtual().name("cotahist-recuperacao").start(() -> {
            try {
                if (service.stale()) {
                    service.importRecent("recuperacao na subida");
                } else {
                    log.info("Cotacoes da B3 em dia: nada a recuperar na subida");
                }
            } catch (RuntimeException e) {
                log.error("Recuperacao das cotacoes da B3 falhou: {}", e.getMessage(), e);
                notices.routineFailed("Recuperação das cotações da B3", e.getMessage());
            }
        });
    }
}
