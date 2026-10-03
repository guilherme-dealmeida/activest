package com.primevest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.catalog.CatalogChange;
import com.primevest.domain.catalog.CatalogMessaging;
import com.primevest.domain.catalog.CatalogMessaging.OnDemandRequestPublisher;
import com.primevest.domain.portfolio.PositionRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Mensagens da carteira com a adm-api pelo Redis (docs/planos/PLANO_ADM_API.md, 10.3):
 * <ul>
 *   <li>ouve "catalogo mudou" e recalcula as posicoes ({@link PositionRefreshService});</li>
 *   <li>publica "preciso do historico" quando uma transacao pede proventos de um ativo.</li>
 * </ul>
 * Desligado com {@code primevest.messaging.enabled=false} (testes): o core poe no lugar
 * publicadores que so registram no log.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = CatalogMessaging.ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
public class CatalogMessagingConfig {

    @Bean
    OnDemandRequestPublisher onDemandRequestPublisher(StringRedisTemplate redis) {
        return assetId -> {
            try {
                redis.convertAndSend(CatalogMessaging.ON_DEMAND_CHANNEL, String.valueOf(assetId));
            } catch (RuntimeException e) {
                // Redis fora: o pedido se perde, a rotina diaria de proventos cobre
                log.warn("Proventos sob demanda do ativo {}: pedido nao publicado ({})", assetId, e.getMessage());
            }
        };
    }

    @Bean
    RedisMessageListenerContainer catalogChangeListener(RedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper,
                                                        PositionRefreshService positionRefreshService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            // fora da thread do Redis: o recalculo pode levar segundos
            Thread.ofVirtual().name("carteira-catalogo").start(() -> {
                try {
                    positionRefreshService.refresh(objectMapper.readValue(body, CatalogChange.class));
                } catch (Exception e) {
                    log.error("Carteira: mensagem de catalogo nao processada ({}): {}", body, e.getMessage(), e);
                }
            });
        }, new ChannelTopic(CatalogMessaging.CATALOG_CHANNEL));
        return container;
    }
}
