package com.primevest.adm.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.adm.domain.asset.AssetLogoService;
import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.domain.catalog.CatalogMessaging;
import com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
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
 * Mensagens do catalogo com a app-api pelo Redis (PLANO_ADM_API.md, 10.3):
 * <ul>
 *   <li>publica "catalogo mudou" ao fim do fechamento e dos proventos;</li>
 *   <li>ouve "preciso do historico" (transacao na carteira) e busca os proventos do ativo e,
 *   se faltar, o logo.</li>
 * </ul>
 * Desligado com {@code primevest.messaging.enabled=false} (testes): o core poe no lugar um
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
                                                   ObjectProvider<ProventosCatalogService> proventos,
                                                   ObjectProvider<AssetLogoService> logos) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            // fila unica do servico (F8, PENDENCIAS 14.1): volta na hora, sem thread por pedido
            try {
                proventos.getObject().requestOnDemand(Long.valueOf(body));
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
}
