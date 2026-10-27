package com.activest.domain.currency;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Câmbio de uma moeda para a moeda base num dia: o boletim de fechamento da PTAX (V9, D22). Gravado só pela adm-api
 * (por SQL, como os índices); leitura pelo {@link ExchangeRateService}.
 */
@Entity
@Table(name = "exchange_rates")
@IdClass(ExchangeRate.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Id
    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Column(name = "buy_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal buyRate;

    @Column(name = "sell_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal sellRate;

    @Column(name = "bulletin_at")
    private LocalDateTime bulletinAt;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BigDecimal rate(RateSide side) {
        return side == RateSide.BUY ? buyRate : sellRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String currencyCode;
        private LocalDate referenceDate;
    }
}
