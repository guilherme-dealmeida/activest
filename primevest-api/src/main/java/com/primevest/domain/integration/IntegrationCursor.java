package com.primevest.domain.integration;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Onde cada fluxo da fonte parou: e o que torna a sincronizacao incremental
 * e respeita os limites de peso da corretora (6.5). O valor e opaco para o
 * banco — ultimo id ou instante, conforme o conector.
 */
@Entity
@Table(name = "integration_cursors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationCursor {

    @EmbeddedId
    private IntegrationCursorId id;

    @Column(name = "cursor_value", nullable = false, length = 200)
    private String cursorValue;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
