package com.primevest.domain.pricehistory;

import com.primevest.domain.asset.Asset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Column(name = "close_price", precision = 18, scale = 8, nullable = false)
    private BigDecimal closePrice;

    @Column(name = "high_price", precision = 18, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 8)
    private BigDecimal lowPrice;

    private Long volume;
}
