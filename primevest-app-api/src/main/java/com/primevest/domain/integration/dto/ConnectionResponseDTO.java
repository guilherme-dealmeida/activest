package com.primevest.domain.integration.dto;

import com.primevest.domain.integration.ConnectionStatus;
import com.primevest.domain.integration.IntegrationProvider;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Uma conexao como a tela mostra. Da chave, so os 4 ultimos caracteres
 * ({@code fingerprint}); o segredo nunca volta (6.4).
 */
public record ConnectionResponseDTO(
    UUID id,
    IntegrationProvider provider,
    String label,
    ConnectionStatus status,
    String fingerprint,
    LocalDateTime validatedAt,
    LocalDateTime lastSyncAt,
    String lastError,
    LocalDateTime createdAt,
    /** A execucao mais recente (fila, pre-visualizacao aguardando, ou a ultima concluida). */
    UUID lastRunId,
    com.primevest.domain.integration.RunStatus lastRunStatus,
    /** Ativos que divergem da corretora na ultima sincronizacao concluida (E3). */
    int divergenceCount
) {}
