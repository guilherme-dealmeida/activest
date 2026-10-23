package com.activest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging;
import com.activest.domain.catalog.CatalogMessaging.OnDemandRequestPublisher;
import com.activest.domain.portfolio.PositionRefreshService;
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
 * Desligado com {@code activest.messaging.enabled=false} (testes): o core poe no lugar
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

    /** Comandos das Tarefas entre os serviços (docs/planos/PLANO_TAREFAS.md, D29). */
    @Bean
    com.activest.domain.task.TaskCommandPublisher taskCommandPublisher(StringRedisTemplate redis,
                                                                        ObjectMapper objectMapper) {
        return command -> {
            try {
                redis.convertAndSend(com.activest.domain.task.TaskCommand.CHANNEL,
                        objectMapper.writeValueAsString(command));
            } catch (Exception e) {
                // Redis fora: o reagendamento sai na releitura de 5 min; o Run some (a tela mostra que nao comecou)
                log.warn("Tarefas: comando {} nao publicado ({})", command, e.getMessage());
            }
        };
    }

    @Bean
    RedisMessageListenerContainer taskCommandListener(RedisConnectionFactory connectionFactory,
                                                      ObjectMapper objectMapper,
                                                      org.springframework.beans.factory.ObjectProvider<
                                                          com.activest.domain.task.TaskCommands> commands) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            Thread.ofVirtual().name("tarefas-comando").start(() -> {
                try {
                    commands.getObject().receive(
                            objectMapper.readValue(body, com.activest.domain.task.TaskCommand.class));
                } catch (Exception e) {
                    log.error("Tarefas: comando nao processado ({}): {}", body, e.getMessage(), e);
                }
            });
        }, new ChannelTopic(com.activest.domain.task.TaskCommand.CHANNEL));
        return container;
    }
}
