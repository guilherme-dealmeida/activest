package com.primevest.domain.dividend;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aliquota de IR retido na fonte de um tipo de provento, num tipo de ativo,
 * numa vigencia (V23, PLANO_IR_PROVENTOS.md).
 *
 * <p>O que a lei muda mora aqui, no banco; o jeito de calcular mora no
 * {@link WithholdingTaxCalculator}. Lei nova = migration que fecha a vigencia
 * da linha antiga, insere a nova e zera {@code dividends.tax_checked_at} dos
 * proventos afetados (ver o cabecalho da V23).</p>
 */
@Entity
@Table(name = "withholding_tax_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithholdingTaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dividend_type", nullable = false)
    private DividendType dividendType;

    /** Por tipo de ativo: dividendo de acao e isento, de BDR nao. */
    @Column(name = "asset_type_id", nullable = false)
    private Long assetTypeId;

    /** 0,1750 = 17,5%; zero = isento. */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_date", nullable = false)
    private TaxBaseDate baseDate;

    /** Inicio da vigencia; nulo = desde sempre. */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** Fim da vigencia (inclusive); nulo = em vigor. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "legal_basis", nullable = false)
    private String legalBasis;

    /** A data esta dentro da vigencia (limites inclusive). */
    public boolean covers(LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom))
                && (validTo == null || !date.isAfter(validTo));
    }
}
