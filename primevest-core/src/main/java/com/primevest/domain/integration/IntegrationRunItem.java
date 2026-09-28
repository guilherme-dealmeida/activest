package com.primevest.domain.integration;

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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Um movimento da fonte dentro de uma execucao (V26: o lote inteiro, nao so o
 * que ficou de fora). A confirmacao le daqui — nao busca na corretora de novo.
 */
@Entity
@Table(name = "integration_run_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationRunItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private IntegrationRun run;

    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RunItemOutcome outcome;

    @Column(length = 500)
    private String reason;

    /** O movimento normalizado (ImportedMovement em JSON, com o bruto da fonte dentro). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String raw;

    /** O lancamento manual que parece ser esta mesma operacao (6.6). */
    @Column(name = "duplicate_of")
    private UUID duplicateOf;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RunItemResolution resolution;

    /** A transacao criada pela confirmacao, quando houve. */
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
