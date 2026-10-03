package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provento em dinheiro de um papel — o fato do catalogo, sem usuario (V20).
 *
 * <p>PETR4 pagou R$ 0,4716 por acao com data-com 21/08/2026 para todo mundo. O
 * que cada usuario recebeu e esse valor x as cotas que ele tinha na data-com, e
 * mora em {@link Dividend}, criado pelo {@link DividendRecalculator}.</p>
 *
 * <p>{@code type} nulo e rotulo que ainda nao sabemos mapear
 * ({@link #getLabel()} guarda o texto da fonte): o evento fica gravado, mas nao
 * gera provento.</p>
 */
@Entity
@Table(name = "asset_cash_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetCashEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    private DividendType type;

    @Column(nullable = false)
    private String label;

    /** Data-com: ultimo dia em que ter a cota da direito ao provento. */
    @Column(name = "data_com", nullable = false)
    private LocalDate dataCom;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    /**
     * De onde veio a data de pagamento, quando nao da fonte do evento (V24):
     * extrato da B3 ou deduzida. Nulo = oficial.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_date_source")
    private PaymentDateSource paymentDateSource;

    @Column(name = "value_per_share", precision = 24, scale = 11, nullable = false)
    private BigDecimal valuePerShare;

    /**
     * Numero da parcela entre linhas iguais (mesmo tipo, data-com e valor) da
     * mesma fonte — o provento pago em duas parcelas (V22).
     */
    @Column(nullable = false)
    @Builder.Default
    private Short seq = 1;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @Column(name = "related_to")
    private String relatedTo;

    private String isin;

    @Column(name = "income_tax_exempt")
    private Boolean incomeTaxExempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashEventSource source;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    protected void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = LocalDateTime.now();
        }
    }
}
