package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.portfolio.PortfolioAsset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_asset_id")
    private PortfolioAsset portfolioAsset;

    @Column(name = "amount_per_share", precision = 18, scale = 8, nullable = false)
    private BigDecimal amountPerShare;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DividendType type;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
