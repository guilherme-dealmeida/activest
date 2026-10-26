package com.activest.domain.goal;

import com.activest.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Meta financeira (docs/planos/PLANO_METAS.md, V8): so o que o usuario decide — tipo, alvo, prazo, escopo e os
 * parametros do tipo (JSONB validado pelo catalogo). Valor atual, progresso, falta e projecao nunca sao gravados aqui
 * (G1): saem da avaliacao na leitura; a fotografia do dia fica em {@link GoalSnapshot}.
 */
@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private GoalLifecycle lifecycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_before_archive", length = 12)
    private GoalLifecycle lifecycleBeforeArchive;

    /** Alvo principal do tipo (R$, %, cotas): nulo nos tipos sem um alvo unico (ALO-2, LP-1 sem renda desejada). */
    @Column(name = "target_value", precision = 18, scale = 8)
    private BigDecimal targetValue;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** Escopo (posicoes que a meta enxerga), JSON: {@code {"kind":"ALL","values":[],"reservation":"WITH"...}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String scope;

    /** Parametros do tipo (o que nao e coluna), JSON validado pelo catalogo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String params;

    @Column(nullable = false)
    private boolean notify;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private int revision;

    /** Primeira vez que o sistema viu o alvo atingido. */
    @Column(name = "achieved_on")
    private LocalDate achievedOn;

    /** Resultado congelado na conclusao (ou no prazo vencido), JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_result", columnDefinition = "jsonb")
    private String finalResult;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.lifecycle == null) {
            this.lifecycle = GoalLifecycle.ATIVA;
        }
        if (this.revision < 1) {
            this.revision = 1;
        }
        if (this.scope == null) {
            this.scope = "{}";
        }
        if (this.params == null) {
            this.params = "{}";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
