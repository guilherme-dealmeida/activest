package com.primevest.domain.catalog;

import com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.primevest.domain.catalog.CatalogMessaging.OnDemandRequestPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mensagens desligadas ({@code primevest.messaging.enabled=false}): os publicadores so
 * registram no log. E o que roda nos testes — sem Redis —, e os testes de ponta a ponta da
 * app-api trocam por implementacoes que chamam o outro lado na hora.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = CatalogMessaging.ENABLED_PROPERTY, havingValue = "false")
public class MessagingFallbackConfig {

    @Bean
    CatalogChangePublisher catalogChangePublisher() {
        return change -> log.debug("Mensagens desligadas: catalogo mudou ({}, {} ativos) nao publicado",
                change.origin(), change.assetIds().size());
    }

    @Bean
    OnDemandRequestPublisher onDemandRequestPublisher() {
        return assetId -> log.debug("Mensagens desligadas: pedido de historico do ativo {} nao publicado", assetId);
    }
}
