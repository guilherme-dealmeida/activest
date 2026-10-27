package com.activest.domain.currency;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Moeda do cadastro (V9, docs/planos/PLANO_CADASTRO_MOEDAS.md). Chave = código ISO 4217 (D1). Uma única moeda base
 * ({@link #isBase()}, BRL): a carteira soma tudo nela (D14) e ela não tem série de câmbio.
 */
@Entity
@Table(name = "currencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Currency {

    /** Código da moeda base enquanto a carteira for só em reais (D14). Os cálculos perguntam {@link #isBase()}. */
    public static final String BRL = "BRL";

    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 6)
    private String symbol;

    @Column(nullable = false)
    @Builder.Default
    private short decimals = 2;

    @Column(name = "is_base", nullable = false)
    @Builder.Default
    private boolean base = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_source", nullable = false, length = 20)
    @Builder.Default
    private CurrencyRateSource rateSource = CurrencyRateSource.BCB_PTAX;

    /** País da moeda (ISO 3166), para a retenção do país de origem quando o ativo não informa; nulo no EUR. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

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
