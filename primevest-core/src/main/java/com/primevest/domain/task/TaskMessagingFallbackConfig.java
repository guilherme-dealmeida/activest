package com.primevest.domain.task;

import com.primevest.domain.catalog.CatalogMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mensagens desligadas ({@code primevest.messaging.enabled=false}, os testes): o comando só vai para o log. */
@Slf4j
@Configuration
@ConditionalOnProperty(name = CatalogMessaging.ENABLED_PROPERTY, havingValue = "false")
public class TaskMessagingFallbackConfig {

    @Bean
    TaskCommandPublisher taskCommandPublisher() {
        return command -> log.info("Tarefas: comando {} (mensagens desligadas)", command);
    }
}
