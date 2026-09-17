package com.primevest.domain.transaction;

import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.portfolio.PortfolioAsset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_asset_id", nullable = false)
    private PortfolioAsset portfolioAsset;

    /**
     * O papel de renda fixa movimentado. Nulo para renda variavel: uma acao nao
     * tem emissor, vencimento nem taxa contratada.
     *
     * <p>A regra "renda fixa exige nota" vive aqui na aplicacao. Um CHECK
     * precisaria enxergar assets.asset_type_id a partir de transactions, o que
     * so um trigger faz — e trigger para validar e divida, nao garantia.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private FixedIncomeNote note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /**
     * Quantidade e preco unitario. Nulos quando o tipo e lancado por valor
     * total ({@link com.primevest.domain.asset.AssetType#isValuedByTotal()}):
     * um CDB aplicado no banco nao tem quantidade nem preco unitario que o
     * usuario conheca, so o valor aplicado.
     */
    @Column(precision = 18, scale = 8)
    private BigDecimal shares;

    @Column(name = "price_per_share", precision = 18, scale = 8)
    private BigDecimal pricePerShare;

    @Column(name = "total_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "brokerage_fee", precision = 18, scale = 2)
    private BigDecimal brokerageFee;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
