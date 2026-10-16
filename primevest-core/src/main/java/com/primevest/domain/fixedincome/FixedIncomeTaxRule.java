package com.primevest.domain.fixedincome;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aliquota de IR ou de IOF de uma faixa de dias corridos desde o aporte, numa vigencia (V52,
 * docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 3.1).
 *
 * <p>O que a lei muda mora aqui, no banco; o jeito de calcular mora no {@link FixedIncomeTaxTable} e no
 * {@link FixedIncomeTaxCalculator}. Lei nova = migration que fecha a vigencia da linha antiga e insere a nova
 * (ver o cabecalho da V52).</p>
 */
@Entity
@Table(name = "fixed_income_tax_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedIncomeTaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private FixedIncomeTax tax;

    /** Nulo = regra geral (todo produto de RF e o Tesouro); preenchido = excecao do produto. */
    @Column(name = "asset_id")
    private Long assetId;

    /** Dias corridos desde o aporte, inclusive. */
    @Column(name = "days_from", nullable = false)
    private Integer daysFrom;

    /** Inclusive; nulo = sem teto. */
    @Column(name = "days_to")
    private Integer daysTo;

    /** 0,2250 = 22,5%; zero = isento. */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    /** Inicio da vigencia; nulo = desde sempre. */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** Fim da vigencia (inclusive); nulo = em vigor. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "legal_basis", nullable = false)
    private String legalBasis;

    /** A data (do resgate) esta dentro da vigencia (limites inclusive). */
    public boolean covers(LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom))
                && (validTo == null || !date.isAfter(validTo));
    }

    /** O prazo (dias corridos) cai na faixa. */
    public boolean coversDays(long days) {
        return days >= daysFrom && (daysTo == null || days <= daysTo);
    }
}
