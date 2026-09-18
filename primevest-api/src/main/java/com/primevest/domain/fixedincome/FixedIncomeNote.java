package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * O papel de renda fixa do usuário: um CDB comprado no BTG em 2026, vencendo em
 * 2029, a 110% do CDI.
 *
 * <p>É a diferença entre o produto e a aplicação. {@code Asset} diz "CDB";
 * esta nota diz QUAL CDB. Emissor, vencimento e taxa são negociados na compra,
 * variam entre duas aplicações do mesmo produto e interessam a um usuário só —
 * por isso não cabiam no catálogo global, onde apareciam na busca de todos.</p>
 *
 * <p>É daqui que sai a valorização: a taxa contratada está nesta linha, não no
 * ativo genérico.</p>
 *
 * <p>No Tesouro Direto, prazo e forma de remuneração são públicos e vêm de
 * {@code AssetTerms}; a nota os copia no momento do lançamento. A cópia é
 * intencional: o que vale para o cálculo é o que foi contratado, e uma correção
 * futura no catálogo não deve reescrever a posição já registrada.</p>
 */
@Entity
@Table(name = "fixed_income_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedIncomeNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /**
     * Quem emitiu. Texto livre por enquanto: virar tabela de emissores é uma
     * decisão que depende de saber quais nomes o usuário realmente digita, e
     * essa lista só existe depois de um tempo de uso.
     */
    @Column
    private String issuer;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    /**
     * A data da compra do título.
     *
     * <p>Preenchida só no Tesouro Direto, onde ela faz dois trabalhos: ordena o
     * FIFO — as vendas saem dos títulos mais antigos primeiro — e é a chave da
     * regra H, que proíbe dois títulos do mesmo ativo comprados no mesmo dia
     * (comprar de novo na mesma data é aumentar a quantidade da compra que já
     * existe).</p>
     *
     * <p>Nula na Renda Fixa: lá o mesmo título recebe vários aportes, inclusive
     * no mesmo dia, e o saldo é por valor, não por cota. O índice único em
     * banco é parcial justamente por isso.</p>
     */
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    /** Como remunera. Ver {@link RateType}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", length = 20)
    private RateType rateType;

    /** Índice de remuneração. Nulo quando prefixado. Ver {@link Indexer}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Indexer indexer;

    /**
     * O número, cujo significado é dado por {@link #rateType}: % a.a. num
     * prefixado, % do índice num pós-fixado, spread num híbrido.
     */
    @Column(name = "indexer_rate", precision = 10, scale = 4)
    private BigDecimal indexerRate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Rótulo curto para telas: "BTG · venc. 2029-06-01". A carteira passa a
     * repetir o mesmo ticker (dois CDBs são duas linhas), então o ticker sozinho
     * deixou de identificar a posição.
     */
    public String getLabel() {
        StringBuilder sb = new StringBuilder();
        if (issuer != null && !issuer.isBlank()) {
            sb.append(issuer);
        }
        if (maturityDate != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("venc. ").append(maturityDate);
        }
        return sb.toString();
    }
}
