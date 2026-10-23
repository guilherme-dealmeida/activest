package com.activest.domain.dividend;

import com.activest.domain.catalog.CatalogMessaging.OnDemandRequestPublisher;
import com.activest.domain.transaction.PositionChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Proventos na hora em que a posicao muda (docs/planos/PLANO_BRAPI.md, F5): "ao lancar
 * uma compra com data anterior ao evento mais antigo guardado do ativo, busca
 * o historico longo uma vez".
 *
 * <p>Os proventos dos eventos que ja estao no catalogo nascem na propria
 * transacao, pelo recalculo. Este ouvinte cobre o que depende de rede: o
 * emissor que nunca foi sincronizado e o historico longo. Quem busca e a
 * adm-api (catalogo, docs/planos/PLANO_ADM_API.md, 10): aqui so se publica o pedido depois
 * do commit; a resposta volta como "catalogo mudou" e a carteira se recalcula
 * ({@code PositionRefreshService}).</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "activest.dividends.enabled", havingValue = "true", matchIfMissing = true)
public class DividendOnDemandListener {

    private final OnDemandRequestPublisher onDemandRequestPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPositionChanged(PositionChangedEvent event) {
        if (event.assetId() == null) {
            return;
        }
        onDemandRequestPublisher.requestHistory(event.assetId());
    }
}
