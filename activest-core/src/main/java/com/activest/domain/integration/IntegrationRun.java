package com.activest.domain.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Uma sincronizacao de uma conexao, com o resultado que a tela mostra:
 * importadas, ja existentes, nao mapeadas e possiveis duplicatas.
 */
@Entity
@Table(name = "integration_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private IntegrationConnection connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private RunTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RunStatus status = RunStatus.QUEUED;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "imported_count", nullable = false)
    @Builder.Default
    private Integer importedCount = 0;

    @Column(name = "existing_count", nullable = false)
    @Builder.Default
    private Integer existingCount = 0;

    @Column(name = "unmapped_count", nullable = false)
    @Builder.Default
    private Integer unmappedCount = 0;

    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private Integer duplicateCount = 0;

    @Column(columnDefinition = "TEXT")
    private String error;

    /** Cursores que viram integration_cursors SO na confirmacao (JSON objeto stream -> valor). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "next_cursors", columnDefinition = "jsonb")
    private String nextCursors;

    /** Saldo da corretora na busca (JSON ativo -> quantidade), para o aviso de divergencia (E3). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String balances;

    /** Soma dos depositos em reais do lote (4.6). */
    @Column(name = "fiat_deposited", precision = 18, scale = 2)
    private BigDecimal fiatDeposited;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
