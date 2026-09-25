package com.primevest.domain.dividend;

import com.primevest.domain.transaction.PositionChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Proventos na hora em que a posicao muda (PLANO_BRAPI.md, F5): "ao lancar
 * uma compra com data anterior ao evento mais antigo guardado do ativo, busca
 * o historico longo uma vez".
 *
 * <p>Os proventos dos eventos que ja estao no catalogo nascem na propria
 * transacao, pelo recalculo. Este ouvinte cobre o que depende de rede: o
 * emissor que nunca foi sincronizado e o historico longo. Roda depois do commit
 * e em segundo plano — o usuario nao espera a B3 para ver a transacao gravada.</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.proventos.enabled", havingValue = "true", matchIfMissing = true)
public class ProventosOnDemandListener {

    private final ProventosSyncService proventosSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPositionChanged(PositionChangedEvent event) {
        if (event.assetId() == null) {
            return;
        }
        Thread.ofVirtual().name("proventos-sob-demanda").start(() -> proventosSyncService.syncAssetOnDemand(event.assetId()));
    }
}
