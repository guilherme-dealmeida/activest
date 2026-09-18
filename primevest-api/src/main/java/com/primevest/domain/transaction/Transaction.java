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

    /**
     * A venda do usuario a que esta linha pertence.
     *
     * <p>No Tesouro Direto o saldo e do ativo e a posicao e do titulo: uma
     * venda de 5 cotas pode consumir dois titulos comprados em datas
     * diferentes, e cada parte vira uma transacao presa ao titulo que ela
     * liquida. O grupo e o que diz que as partes sao a MESMA venda — e por isso
     * editar e excluir agem sobre ele, nunca sobre a linha isolada.</p>
     *
     * <p>Nulo em compra e nos tipos cujo saldo nao e por titulo.</p>
     */
    @Column(name = "sale_group_id")
    private UUID saleGroupId;

    /**
     * Venda de Renda Fixa marcada como liquidacao total.
     *
     * <p>Fica na transacao, e nao so na posicao, porque a regra precisa dela
     * duas vezes: para reabrir o formulario com a marca certa e para reavaliar
     * a venda quando o valor dela e editado. O is_settled da posicao e
     * derivado daqui.</p>
     */
    @Column(name = "is_full_settlement", nullable = false)
    @Builder.Default
    private boolean fullSettlement = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
