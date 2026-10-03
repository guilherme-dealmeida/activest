package com.primevest.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as rotinas agendadas (cotações e proventos).
 *
 * <p>Saiu de {@code PrimevestApplication} para poder ser desligada por
 * propriedade. Ligada por padrão — em dev, docker e prod nada muda. O perfil
 * {@code test} desliga: uma rotina que dispara no meio de um teste mexe em
 * valorização e proventos por fora do cenário, e ainda tenta sair para a rede.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
