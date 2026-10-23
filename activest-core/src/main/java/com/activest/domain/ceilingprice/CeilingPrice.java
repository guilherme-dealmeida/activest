package com.activest.domain.ceilingprice;

import com.activest.domain.asset.Asset;
import com.activest.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cálculo de preço teto de um usuário para um ativo (modelo PREÇO TETO V2 — docs/planos/PLANO_PRECO_TETO.md, 4.1).
 * Guarda <b>só as entradas</b>, todas obrigatórias; o resultado sai delas (CeilingPriceRules, app-api). Um por
 * (usuário, ativo), sobrescrito a cada "Salvar". Percentuais em pontos.
 */
@Entity
@Table(name = "ceiling_prices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CeilingPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private Long shares;

    @Column(name = "roe_pct", precision = 9, scale = 4, nullable = false)
    private BigDecimal roePct;

    @Column(name = "payout_pct", precision = 9, scale = 4, nullable = false)
    private BigDecimal payoutPct;

    @Column(name = "discount_rate_pct", precision = 9, scale = 4, nullable = false)
    private BigDecimal discountRatePct;

    @Column(name = "perpetual_growth_pct", precision = 9, scale = 4, nullable = false)
    private BigDecimal perpetualGrowthPct;

    @Column(name = "safety_margin_pct", precision = 9, scale = 4, nullable = false)
    private BigDecimal safetyMarginPct;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal price;

    /** Por campo: origem (PADRAO/INFORMADO), referência e valor do padrão no momento do "Salvar". */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inputs_origin", columnDefinition = "jsonb", nullable = false)
    private String inputsOrigin;

    @Column(length = 500)
    private String notes;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    /** "Alertar preço abaixo do ideal" (V49; docs/planos/PLANO_NOTIFICACOES.md, 5.5). */
    @Column(name = "alert_enabled", nullable = false)
    @Builder.Default
    private boolean alertEnabled = false;

    /** O preço estava abaixo do ideal na última conferência: só avisa de novo depois de voltar acima. */
    @Column(name = "alert_below_ideal", nullable = false)
    @Builder.Default
    private boolean alertBelowIdeal = false;

    /** Último dia avisado: no máximo um aviso por ativo por dia. */
    @Column(name = "alert_last_on")
    private java.time.LocalDate alertLastOn;

    /** Os quatro lucros, T-3 … T. */
    @OneToMany(mappedBy = "ceilingPrice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    @Builder.Default
    private List<CeilingPriceQuarter> quarters = new ArrayList<>();

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
}
