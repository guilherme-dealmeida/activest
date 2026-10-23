package com.activest.domain.corporateaction;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Custo por acao de uma bonificacao, informado pelo usuario para a posicao dele (V51; docs/planos/
 * PLANO_NOTIFICACOES.md, 5.4). So vale quando o catalogo nao tem o custo atribuido.
 */
@Entity
@Table(name = "stock_event_costs")
@IdClass(StockEventCost.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class StockEventCost {

    @Id
    @Column(name = "portfolio_asset_id")
    private UUID portfolioAssetId;

    @Id
    @Column(name = "stock_event_id")
    private Long stockEventId;

    @Column(name = "cost_per_share", precision = 18, scale = 8, nullable = false)
    private BigDecimal costPerShare;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public StockEventCost(UUID portfolioAssetId, Long stockEventId, BigDecimal costPerShare) {
        this.portfolioAssetId = portfolioAssetId;
        this.stockEventId = stockEventId;
        this.costPerShare = costPerShare;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID portfolioAssetId;
        private Long stockEventId;

        public Key(UUID portfolioAssetId, Long stockEventId) {
            this.portfolioAssetId = portfolioAssetId;
            this.stockEventId = stockEventId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(portfolioAssetId, k.portfolioAssetId)
                && Objects.equals(stockEventId, k.stockEventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(portfolioAssetId, stockEventId);
        }
    }
}
