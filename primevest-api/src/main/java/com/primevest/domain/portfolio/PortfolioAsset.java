package com.primevest.domain.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.primevest.domain.asset.Asset;
import com.primevest.domain.dividend.Dividend;
import com.primevest.domain.transaction.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolio_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "total_shares", precision = 18, scale = 8)
    private BigDecimal totalShares;

    @Column(name = "average_price", precision = 18, scale = 8)
    private BigDecimal averagePrice;

    @Column(name = "total_invested", precision = 18, scale = 2)
    private BigDecimal totalInvested;

    @Column(name = "current_value", precision = 18, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "total_dividends", precision = 18, scale = 2)
    private BigDecimal totalDividends;

    @Column(name = "is_reservation", nullable = false)
    private boolean isReservation;

    @JsonIgnore
    @OneToMany(mappedBy = "portfolioAsset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "portfolioAsset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Dividend> dividends = new ArrayList<>();
}
