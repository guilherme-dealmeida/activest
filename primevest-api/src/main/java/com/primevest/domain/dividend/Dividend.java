package com.primevest.domain.dividend;

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

    /**
     * A posicao que recebeu o provento. E o unico caminho ate o ativo
     * (provento -> posicao -> ativo) e ate o dono (-> carteira -> usuario).
     *
     * <p>Antes havia tambem asset_id, um segundo caminho para o mesmo ativo que
     * podia divergir do da posicao, e portfolio_asset_id era opcional. Provento
     * sem posicao nao tinha dono e sumia ate para quem o recebeu.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_asset_id", nullable = false)
    private PortfolioAsset portfolioAsset;

    @Column(name = "amount_per_share", precision = 18, scale = 8, nullable = false)
    private BigDecimal amountPerShare;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DividendType type;

    /**
     * Evento do catalogo que gerou este provento. Nulo no MANUAL (V20).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_event_id")
    private AssetCashEvent cashEvent;

    /** Cotas da posicao na data-com, gravadas para conferencia (V20). */
    @Column(name = "shares_on_date", precision = 18, scale = 8)
    private BigDecimal sharesOnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DividendOrigin origin = DividendOrigin.MANUAL;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
