package com.primevest.adm.scheduler;

import com.primevest.adm.domain.pricehistory.ClosingPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina de fechamento (docs/planos/PLANO_BRAPI.md, secao 3, B9 e B12).
 *
 * <p>Substitui o PriceUpdateScheduler, que atualizava a B3 a cada 15 minutos
 * no pregao e o catalogo inteiro de uma vez — no plano gratuito cada lote de 15
 * tickers voltava 400 e nada era atualizado. Agora so fechamento:</p>
 * <ul>
 *   <li><b>B3</b> — 19:00, segunda a sexta;</li>
 *   <li><b>cripto</b> — 19:00, todos os dias (cripto negocia no fim de semana).</li>
 * </ul>
 * <p>Horarios em propriedade ({@code primevest.closing.*}), fuso de Sao Paulo.</p>
 *
 * <p><b>Recuperacao:</b> a API roda na maquina do usuario; se ela estiver
 * desligada as 19h, o dia fica sem preco. Na subida, se faltar o ultimo
 * fechamento de algum ativo em carteira, a rodada roda na hora — em segundo
 * plano, para nao segurar a subida.</p>
 *
 * <p>Desligada no perfil de teste, junto com as demais rotinas
 * ({@code primevest.scheduling.enabled}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ClosingPriceScheduler {

    /** Falha da rotina vira mensagem de administrador (docs/planos/PLANO_NOTIFICACOES.md, 5.7). */
    @org.springframework.beans.factory.annotation.Autowired
    com.primevest.adm.domain.notice.AdminNotices notices;

    private final ClosingPriceService closingPriceService;

    public ClosingPriceScheduler(ClosingPriceService closingPriceService) {
        this.closingPriceService = closingPriceService;
    }

    @Scheduled(cron = "${primevest.closing.b3-cron:0 0 19 * * MON-FRI}", zone = "America/Sao_Paulo")
    public void b3Closing() {
        closingPriceService.runB3Closing("19:00");
    }

    @Scheduled(cron = "${primevest.closing.crypto-cron:0 0 19 * * *}", zone = "America/Sao_Paulo")
    public void cryptoClosing() {
        closingPriceService.runCryptoClosing("19:00");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        Thread.ofVirtual().name("fechamento-recuperacao").start(() -> {
            try {
                if (closingPriceService.b3NeedsRecovery()) {
                    closingPriceService.runB3Closing("recuperacao na subida");
                } else {
                    log.info("Fechamento B3 em dia: nada a recuperar na subida");
                }
                if (closingPriceService.cryptoNeedsRecovery()) {
                    closingPriceService.runCryptoClosing("recuperacao na subida");
                }
            } catch (RuntimeException e) {
                log.error("Recuperacao do fechamento falhou: {}", e.getMessage(), e);
                notices.routineFailed("Recuperação do fechamento", e.getMessage());
            }
        });
    }
}
