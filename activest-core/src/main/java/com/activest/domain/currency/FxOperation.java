package com.activest.domain.currency;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Operação de câmbio do usuário (V9, D19): remessa para a corretora no exterior ou retorno. Insumo do saldo em moeda
 * estrangeira e do custo do dólar (plano, 6.3); o IOF sai da regra por data e finalidade.
 */
@Entity
@Table(name = "fx_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    @Column(name = "from_currency_code", nullable = false, length = 3)
    private String fromCurrencyCode;

    @Column(name = "to_currency_code", nullable = false, length = 3)
    private String toCurrencyCode;

    @Column(name = "amount_sent", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountSent;

    @Column(name = "amount_received", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountReceived;

    @Column(name = "effective_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal effectiveRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FxPurpose purpose;

    @Column(name = "iof_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal iofRate;

    @Column(name = "iof_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal iofAmount;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(length = 120)
    private String institution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "foreign_tax_rule_id")
    private ForeignTaxRule foreignTaxRule;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
