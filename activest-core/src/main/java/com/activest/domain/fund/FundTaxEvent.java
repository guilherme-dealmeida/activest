package com.activest.domain.fund;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Come-cotas de um lote de fundo (V12, D22, C9): no último dia útil de maio e de novembro o IR antecipado reduz as
 * cotas do lote. Gravado para que Carteira, Metas, Rentabilidade e integrações leiam a mesma quantidade; regerado
 * quando uma transação ou a cota muda.
 */
@Entity
@Table(name = "fund_tax_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundTaxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** A compra que originou o lote. */
    @Column(name = "lot_transaction_id", nullable = false)
    private UUID lotTransactionId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String kind = "COME_COTAS";

    /** Cota do dia do come-cotas. */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quote;

    /** Cota-base do lote antes do evento (compra ou come-cotas anterior). */
    @Column(name = "base_quote", nullable = false, precision = 18, scale = 8)
    private BigDecimal baseQuote;

    @Column(name = "shares_before", nullable = false, precision = 18, scale = 8)
    private BigDecimal sharesBefore;

    @Column(name = "shares_withheld", nullable = false, precision = 18, scale = 8)
    private BigDecimal sharesWithheld;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    @Column(name = "fixed_income_tax_rule_id")
    private Long fixedIncomeTaxRuleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
