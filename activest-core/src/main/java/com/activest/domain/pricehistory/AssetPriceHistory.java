package com.activest.domain.pricehistory;

import com.activest.domain.asset.Asset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_price_history", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"asset_id", "reference_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "open_price", precision = 18, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "close_price", precision = 18, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "high_price", precision = 18, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 8)
    private BigDecimal lowPrice;

    private Long volume;

    /** Origem do pregao (V29). A BRAPI nao sobrescreve dia gravado pela B3. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private PriceHistorySource source = PriceHistorySource.BRAPI;

    /**
     * Criacao e ultima alteracao da linha. Preenchidas pelos callbacks
     * {@code PrePersist} e {@code PreUpdate} abaixo, como no resto do projeto:
     * nao se usa {@code CreationTimestamp} do Hibernate aqui.
     */
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
