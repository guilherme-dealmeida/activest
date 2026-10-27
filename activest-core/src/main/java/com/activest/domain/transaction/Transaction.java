package com.activest.domain.transaction;

import com.activest.domain.fixedincome.FixedIncomeNote;
import com.activest.domain.integration.IntegrationConnection;
import com.activest.domain.portfolio.PortfolioAsset;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
     * total ({@link com.activest.domain.asset.AssetType#isValuedByTotal()}):
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

    /**
     * Venda de renda fixa e de Tesouro (V53, docs/planos/PLANO_VENDA_RENDA_FIXA.md): o LIQUIDO. Na renda fixa e o que
     * o usuario digitou — a fonte; o bruto ({@link #totalAmount}) e calculado dele pelos lotes e regravado a cada
     * recalculo. No Tesouro e calculado (bruto - IR - IOF - taxa). Nulo nos demais.
     */
    @Column(name = "net_amount", precision = 18, scale = 2)
    private BigDecimal netAmount;

    /** IR da venda de renda fixa ou Tesouro (soma dos lotes/partes). Anda junto com {@link #netAmount}. */
    @Column(name = "income_tax", precision = 18, scale = 2)
    private BigDecimal incomeTax;

    /** IOF da venda de renda fixa ou Tesouro. Anda junto com {@link #netAmount}. */
    @Column(name = "iof_tax", precision = 18, scale = 2)
    private BigDecimal iofTax;

    /**
     * Liquidacao total da renda fixa: bruto menos o saldo da curva na data (o "ajuste da liquidacao", R8). Mostra o
     * quanto o Activest errou contra o banco; negativo quando o banco pagou menos.
     */
    @Column(name = "settlement_adjustment", precision = 18, scale = 2)
    private BigDecimal settlementAdjustment;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    // ----- moeda (V9, docs/planos/PLANO_CADASTRO_MOEDAS.md, D3) -----
    // As colunas acima (preco, total, taxa, liquido, IR) continuam na MOEDA BASE (reais): e o que todos os calculos
    // leem. Em ativo de outra moeda, o lancado pelo usuario fica nos campos original* e o cambio em fxRate.

    /** Moeda da transação = moeda do ativo (não escolhida pelo usuário). */
    @Column(name = "currency_code", nullable = false, length = 3)
    @Builder.Default
    private String currencyCode = com.activest.domain.currency.Currency.BRL;

    /** Câmbio para a moeda base: 1 na base; PTAX venda da data por padrão nas demais (D5/D16), editável. */
    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal fxRate = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "fx_rate_origin", nullable = false, length = 10)
    @Builder.Default
    private com.activest.domain.currency.FxRateOrigin fxRateOrigin = com.activest.domain.currency.FxRateOrigin.BASE;

    @Column(name = "original_price_per_share", precision = 18, scale = 8)
    private BigDecimal originalPricePerShare;

    @Column(name = "original_total_amount", precision = 18, scale = 2)
    private BigDecimal originalTotalAmount;

    @Column(name = "original_fee", precision = 18, scale = 2)
    private BigDecimal originalFee;

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

    /**
     * Digitada ou importada (V25). Importada carrega os quatro campos abaixo;
     * o banco confere a combinacao (ck_transactions_origin).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private TransactionOrigin origin = TransactionOrigin.MANUAL;

    /**
     * Id estavel da operacao na fonte (regra R5 do plano), unico por conexao:
     * e o que torna a reimportacao idempotente.
     */
    @Column(name = "external_id", length = 200)
    private String externalId;

    /**
     * Conexao de onde veio. Fica nula se a conexao for apagada e o usuario
     * escolher manter as transacoes (ON DELETE SET NULL).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private IntegrationConnection connection;

    /**
     * Instante exato da operacao na fonte. {@link #transactionDate} continua
     * sendo a data de negocio, no fuso de Brasilia. Nulo nas manuais.
     */
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /**
     * O usuario editou uma importada: a sincronizacao nao toca mais nela
     * (regra E1). Nulo = identica a fonte.
     */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    /** Os valores como vieram da fonte, para "restaurar" (regra E4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_snapshot", columnDefinition = "jsonb")
    private String sourceSnapshot;

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
