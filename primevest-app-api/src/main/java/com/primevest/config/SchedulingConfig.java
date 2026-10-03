package com.primevest.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as rotinas agendadas (cotações e proventos).
 *
 * <p>Saiu de {@code PrimevestApplication} para poder ser desligada por
 * propriedade. Ligada por padrão — em dev, docker e prod nada muda. O perfil
 * {@code test} desliga: uma rotina que dispara no meio de um teste mexe em
 * valorização e proventos por fora do cenário, e ainda tenta sair para a rede.</p>
 *
 * <p>Cada rotina tem a sua condição. O agendador também liga só para o worker da fila de
 * integrações ({@code primevest.integrations.worker.enabled}): na pilha primevest-teste as
 * rotinas ficam desligadas (D3), mas a sincronização pedida pelo usuário precisa ser processada
 * (docs/planos/PLANO_INTEGRACAO_B3.md, F5).</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnExpression("${primevest.scheduling.enabled:true} or ${primevest.integrations.worker.enabled:false}")
public class SchedulingConfig {
}
