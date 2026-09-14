package com.primevest.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_category_id")
    private AssetCategory assetCategory;

    @Column(name = "current_price", precision = 18, scale = 8)
    private BigDecimal currentPrice;

    @Column(name = "price_updated_at")
    private LocalDateTime priceUpdatedAt;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    /** Como remunera. Ver {@link RateType}. Nulo para renda variável. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", length = 20)
    private RateType rateType;

    /** Índice de remuneração. Nulo quando prefixado. Ver {@link Indexer}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Indexer indexer;

    /** Significado dado por {@link #rateType}: % a.a., % do índice, ou spread. */
    @Column(name = "indexer_rate", precision = 10, scale = 4)
    private BigDecimal indexerRate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "custom", nullable = false)
    @Builder.Default
    private boolean custom = false;

}

