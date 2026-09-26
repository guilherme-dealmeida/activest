package com.primevest.domain.integration.dto;

import java.util.Map;

/**
 * Confirmar uma execucao. So as possiveis duplicatas precisam de decisao; o
 * resto (NEW) entra, e o que nao e importavel continua de fora.
 *
 * @param duplicates externalId -> REPLACE (importa e apaga o manual),
 *                   KEEP_BOTH (importa e mantem o manual) ou SKIP (nao importa)
 */
public record ConfirmImportDTO(Map<String, DuplicateDecision> duplicates) {

    public enum DuplicateDecision { REPLACE, KEEP_BOTH, SKIP }

    public ConfirmImportDTO {
        duplicates = duplicates == null ? Map.of() : Map.copyOf(duplicates);
    }
}
