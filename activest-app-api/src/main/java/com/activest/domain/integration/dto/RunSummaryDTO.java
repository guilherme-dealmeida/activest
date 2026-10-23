package com.activest.domain.integration.dto;

import com.activest.domain.integration.RunStatus;
import com.activest.domain.integration.RunTrigger;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha do historico de sincronizacoes de uma conexao. */
public record RunSummaryDTO(
    UUID runId,
    RunStatus status,
    RunTrigger trigger,
    LocalDateTime createdAt,
    LocalDateTime finishedAt,
    int importedCount,
    int existingCount,
    int unmappedCount,
    int duplicateCount,
    String error
) {}
