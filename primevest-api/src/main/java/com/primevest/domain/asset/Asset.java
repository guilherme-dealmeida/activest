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

    private String sector;

    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "price_updated_at")
    private LocalDateTime priceUpdatedAt;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    private String indexer;

    @Column(name = "indexer_rate", precision = 19, scale = 4)
    private BigDecimal indexerRate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "custom", nullable = false)
    @Builder.Default
    private boolean custom = false;

}

