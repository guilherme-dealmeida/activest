package com.primevest.integration.b3investidor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Liga a B3 so com {@code b3i.enabled=true} (docs/planos/PLANO_INTEGRACAO_B3.md, 6.13): sem ela, nem o
 * cliente nem o conector sobem, a B3 some da lista de fontes e o resto da API segue normal.
 */
@Configuration
@ConditionalOnProperty(prefix = "b3i", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(B3InvestidorProperties.class)
public class B3InvestidorConfig {
}
