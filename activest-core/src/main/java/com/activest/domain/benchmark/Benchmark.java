package com.activest.domain.benchmark;

import com.activest.domain.asset.Asset;
import com.activest.domain.marketindex.MarketIndex;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Um benchmark da Rentabilidade (V57, docs/planos/PLANO_RENTABILIDADE.md, 3.1). Dado de referencia, semeado pela
 * migration; so leitura no codigo. Aponta um indice de mercado ({@link BenchmarkKind#INDICE}) ou um ativo do
 * catalogo ({@link BenchmarkKind#ATIVO}); a serie fica onde ja esta. Resolva sempre por {@code code}.
 */
@Entity
@Table(name = "benchmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Benchmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenchmarkCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BenchmarkKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_index_id")
    private MarketIndex marketIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    /** Aceita "+ x% a.a." informado na consulta (IPCA + x%). */
    @Column(name = "accepts_spread", nullable = false)
    private boolean acceptsSpread;

    @Column(length = 400)
    private String note;

    @Column(name = "default_selected", nullable = false)
    private boolean defaultSelected;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
