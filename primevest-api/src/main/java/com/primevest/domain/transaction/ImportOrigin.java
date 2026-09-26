package com.primevest.domain.transaction;

import com.primevest.domain.integration.IntegrationConnection;

import java.time.LocalDateTime;

/**
 * De onde veio uma transacao importada (V25): a conexao, o id estavel da
 * operacao na fonte, o instante exato e o movimento como veio (JSON), guardado
 * para o "restaurar" da regra E4.
 */
public record ImportOrigin(
    IntegrationConnection connection,
    String externalId,
    LocalDateTime executedAt,
    String sourceSnapshot
) {}
