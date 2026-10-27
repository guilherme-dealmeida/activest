package com.activest.domain.currency;

import com.activest.domain.asset.AssetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Regra de tributo do exterior com vigência (V9), no estilo de {@code withholding_tax_rules} e
 * {@code fixed_income_tax_rules}: alíquota por data e base legal. Seed por migration (D18). Faixas progressivas e
 * isenção mensal ficam em {@link #getBrackets()} (JSON), lidas pelo {@link ForeignTaxCalculator}.
 */
@Entity
@Table(name = "foreign_tax_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForeignTaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ForeignTax tax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ForeignTaxEvent event;

    /** País de origem do ativo (ISO 3166); nulo = qualquer. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** Tipo de ativo; nulo = qualquer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id")
    private AssetType assetType;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String brackets;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "legal_basis", nullable = false, length = 300)
    private String legalBasis;

    public boolean validOn(LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom)) && (validTo == null || !date.isAfter(validTo));
    }
}
