package com.activest.adm.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.adm.domain.asset.AssetLogoService;
import com.activest.adm.domain.dividend.DividendCatalogService;
import com.activest.domain.catalog.CatalogMessaging;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Mensagens do catalogo com a app-api pelo Redis (docs/planos/PLANO_ADM_API.md, 10.3):
 * <ul>
 *   <li>publica "catalogo mudou" ao fim do fechamento e dos proventos;</li>
 *   <li>ouve "preciso do historico" (transacao na carteira) e busca os proventos do ativo e,
 *   se faltar, o logo.</li>
 * </ul>
 * Desligado com {@code activest.messaging.enabled=false} (testes): o core poe no lugar um
 * publicador que so registra no log.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = CatalogMessaging.ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
public class AdmMessagingConfig {

    @Bean
    CatalogChangePublisher catalogChangePublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return change -> {
            try {
                redis.convertAndSend(CatalogMessaging.CATALOG_CHANNEL, objectMapper.writeValueAsString(change));
            } catch (JsonProcessingException | RuntimeException e) {
                // Redis fora: a carteira se acerta na rotina diaria dela (21:00)
                log.warn("Catalogo mudou ({}, {} ativos): aviso nao publicado ({})",
                        change.origin(), change.assetIds().size(), e.getMessage());
            }
        };
    }

    /** {@code ObjectProvider}: o servico de proventos depende do publicador acima. */
    @Bean
    RedisMessageListenerContainer onDemandListener(RedisConnectionFactory connectionFactory,
                                                   ObjectProvider<DividendCatalogService> dividends,
                                                   ObjectProvider<AssetLogoService> logos,
                                                   ObjectProvider<com.activest.domain.task.TaskTypeRepository> types) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            // fila unica do servico (F8, PENDENCIAS 14.1): volta na hora, sem thread por pedido.
            // O tipo DIVIDENDS_SYNC desligado desliga tambem o sob demanda (docs/planos/PLANO_TAREFAS.md, D30).
            try {
                boolean enabled = types.getObject().findByType("DIVIDENDS_SYNC")
                        .map(com.activest.domain.task.TaskType::isEnabled).orElse(true);
                if (enabled) {
                    dividends.getObject().requestOnDemand(Long.valueOf(body));
                } else {
                    log.info("Proventos sob demanda ({}): tipo DIVIDENDS_SYNC desligado, pedido ignorado", body);
                }
            } catch (RuntimeException e) {
                log.error("Proventos sob demanda ({}): {}", body, e.getMessage(), e);
            }
            // ativo que entrou numa carteira e ainda nao tem logo (F10: sem busca na subida)
            try {
                logos.getObject().fetchIfMissing(Long.valueOf(body));
            } catch (RuntimeException e) {
                log.warn("Logo sob demanda ({}): {}", body, e.getMessage());
            }
        }, new ChannelTopic(CatalogMessaging.ON_DEMAND_CHANNEL));
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
