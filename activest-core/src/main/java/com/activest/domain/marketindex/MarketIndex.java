package com.activest.domain.marketindex;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cadastro de um indice de mercado (V43, docs/planos/PLANO_INDICES_MERCADO.md). Dado de referencia, semeado
 * pela migration; so leitura no codigo.
 *
 * <p>{@code code} tem o mesmo nome do enum {@code Indexer} para os seis indexadores de renda fixa: a nota acha a
 * serie por {@code fixed_income_notes.indexer}. Nunca resolva indice por id — sempre por {@code code}.</p>
 */
@Entity
@Table(name = "market_indices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IndexPeriodicity periodicity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IndexUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IndexSource source;

    /** Codigo da serie no SGS ("12") ou sigla do indice na B3 ("IBOV"). */
    @Column(name = "source_ref", nullable = false, length = 40)
    private String sourceRef;

    /** Inicio da carga historica padrao (D2: 2010). */
    @Column(name = "history_from", nullable = false)
    private LocalDate historyFrom;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
