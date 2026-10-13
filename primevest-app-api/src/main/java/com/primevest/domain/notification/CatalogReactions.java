package com.primevest.domain.notification;

import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.portfolio.BonusCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** As reações das mensagens ao "catálogo mudou" (docs/planos/PLANO_NOTIFICACOES.md, 5.4 e 5.7). */
@Configuration
@RequiredArgsConstructor
public class CatalogReactions {

    /** Evento novo ou completado: a bonificação sem custo vira pendência para quem tinha a posição. */
    @Bean
    CatalogReaction bonusCostReaction(BonusCostService bonusCosts) {
        return change -> {
            if (change.origin() == CatalogChange.Origin.PROVENTOS) {
                bonusCosts.check();
            }
        };
    }

    /** Depois do fechamento: o aviso de catálogo (sumido, renomeado, sem cotação) chega a quem tem a posição. */
    @Bean
    CatalogReaction catalogNoticeReaction(CatalogNoticeFanout fanout) {
        return change -> {
            if (change.origin() == CatalogChange.Origin.FECHAMENTO) {
                fanout.run();
            }
        };
    }
}
