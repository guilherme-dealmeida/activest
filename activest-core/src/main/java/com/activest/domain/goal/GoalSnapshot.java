package com.activest.domain.goal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fotografia diaria da meta (D3): o que ela mostrava naquele dia, com a revisao vigente. E auditoria — nao se
 * reescreve quando um dado do passado muda; a evolucao recalculada sai na leitura.
 */
@Entity
@Table(name = "goal_snapshots")
@IdClass(GoalSnapshot.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalSnapshot {

    @Id
    @Column(name = "goal_id")
    private UUID goalId;

    @Id
    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Column(nullable = false)
    private int revision;

    @Column(precision = 18, scale = 8)
    private BigDecimal indicator;

    @Column(precision = 18, scale = 8)
    private BigDecimal target;

    @Column(name = "progress_pct", precision = 12, scale = 2)
    private BigDecimal progressPct;

    @Column(nullable = false, length = 30)
    private String situation;

    @Column(nullable = false, length = 15)
    private String quality;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String result;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID goalId;
        private LocalDate referenceDate;
    }
}
