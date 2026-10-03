package com.primevest.adm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as rotinas agendadas do catalogo (fechamento, proventos, balancos, logos, conferencia).
 * O perfil {@code test} desliga, como na app-api.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AdmSchedulingConfig {
}
