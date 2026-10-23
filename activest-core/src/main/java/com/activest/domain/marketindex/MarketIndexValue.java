package com.activest.domain.marketindex;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um valor da serie de um indice, como a fonte publica (V43). Mensal: {@code referenceDate} e o dia 1o do mes.
 * Gravado so pela adm-api ({@code MarketIndexImportService}, por SQL); so leitura por aqui.
 */
@Entity
@Table(name = "market_index_values")
@IdClass(MarketIndexValue.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MarketIndexValue {

    @Id
    @Column(name = "index_id")
    private Long indexId;

    @Id
    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long indexId;
        private LocalDate referenceDate;
    }
}
