package com.primevest.domain.corporateaction;

import com.primevest.domain.asset.Asset;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Desdobramento, grupamento ou bonificacao de um papel — fato do catalogo,
 * sem usuario (V20).
 *
 * <p>Nao gera linha por usuario: entra no calculo da posicao
 * ({@code PositionCalculator}) como um passo na linha do tempo, logo depois
 * das transacoes da data-com. Ver {@link CorporateActionApplier}.</p>
 */
@Entity
@Table(name = "asset_stock_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetStockEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockEventType type;

    @Column(name = "data_com", nullable = false)
    private LocalDate dataCom;

    @Column(precision = 24, scale = 11, nullable = false)
    private BigDecimal factor;

    /** Custo atribuido por acao bonificada. Nulo = a B3 nao informou: custo zero provisorio (B27). */
    @Column(name = "assigned_cost", precision = 18, scale = 8)
    private BigDecimal assignedCost;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    private String isin;

    @Column(nullable = false)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    protected void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = LocalDateTime.now();
        }
    }

    public StockEventStep toStep() {
        return new StockEventStep(dataCom, type, factor, assignedCost);
    }

    /**
     * O passo para uma posicao: o custo do catalogo, ou, sem ele, o que o usuario informou para a posicao
     * ({@code stock_event_costs}, V51). Nenhum dos dois = zero provisorio.
     */
    public StockEventStep toStep(BigDecimal userCost) {
        return new StockEventStep(dataCom, type, factor, assignedCost != null ? assignedCost : userCost);
    }
}
