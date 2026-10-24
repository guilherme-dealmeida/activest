package com.activest.domain.ceilingprice;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um dos quatro lucros de um cálculo de preço teto (docs/planos/PLANO_PRECO_TETO.md, 4.2): o valor como foi
 * digitado, a unidade, a moeda, o câmbio e o valor em reais que entra no cálculo.
 */
@Entity
@Table(name = "ceiling_price_quarters")
@IdClass(CeilingPriceQuarter.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CeilingPriceQuarter {

    public static final String PADRAO = "PADRAO";
    public static final String INFORMADO = "INFORMADO";

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceiling_price_id", nullable = false)
    private CeilingPrice ceilingPrice;

    /** 1 = T-3 … 4 = T. */
    @Id
    @Column(nullable = false)
    private Short position;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "original_value", precision = 24, scale = 2, nullable = false)
    private BigDecimal originalValue;

    /** UNIDADE, MIL, MILHAO, BILHAO. */
    @Column(nullable = false, length = 8)
    private String unit;

    /** BRL ou USD. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "fx_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal fxRate;

    @Column(name = "value_brl", precision = 24, scale = 2, nullable = false)
    private BigDecimal valueBrl;

    /** PADRAO ou INFORMADO. */
    @Column(nullable = false, length = 10)
    private String origin;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID ceilingPrice;
        private Short position;
    }
}
