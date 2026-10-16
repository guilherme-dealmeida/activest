package com.primevest.domain.fixedincome;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Empresa do cadastro de renda fixa: emissora e/ou distribuidora de um titulo
 * (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secoes 2 e 3.1).
 *
 * <p>Catalogo global, como {@code assets}: vem da integracao BCB + CVM (adm-api) e do seed com o estado conferido.
 * Chave natural: a raiz do CNPJ ({@link #cnpjRoot}, 8 digitos). Empresa morta nunca sai (lancamento retroativo):
 * guarda a {@link #status}.</p>
 *
 * <p>Sem vinculo com {@code issuers} (os emissores da bolsa): a mesma companhia pode estar nas duas tabelas, e o
 * CNPJ e a ponte quando for preciso (L1 do plano).</p>
 */
@Entity
@Table(name = "fixed_income_issuers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedIncomeIssuer {

    public enum GroupSource { BCB, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private FixedIncomeIssuerKind kind;

    /** Raiz do CNPJ, 8 digitos. Nula so nas duas linhas fixas (Tesouro Nacional, nao listada). */
    @Column(name = "cnpj_root", length = 8, unique = true)
    private String cnpjRoot;

    /** CNPJ completo, quando a fonte traz. So informacao. */
    @Column(length = 14)
    private String cnpj;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(length = 120)
    private String segment;

    @Column(length = 30)
    private String status;

    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Guarantor guarantor = Guarantor.NENHUM;

    /** Regra de saida dos lotes quando a empresa e a distribuidora do titulo (R12). */
    @Enumerated(EnumType.STRING)
    @Column(name = "sale_rule", nullable = false, length = 15)
    @Builder.Default
    private SaleRule saleRule = SaleRule.DEFAULT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private FixedIncomeGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_source", length = 10)
    private GroupSource groupSource;

    /** Fontes onde a empresa apareceu (BCB, CVM_INTERMED, CVM_CIA, CVM_SECURIT), separadas por virgula. */
    @Column(length = 60)
    private String sources;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
     * Nome de exibicao (R10): o do grupo; sem grupo, o nome comercial; sem ele, a razao social.
     */
    public String getDisplayName() {
        if (group != null && group.getName() != null && !group.getName().isBlank()) {
            return group.getName();
        }
        if (tradeName != null && !tradeName.isBlank()) {
            return tradeName;
        }
        return legalName;
    }

    /** Rotulo dos seletores (R10): "Grupo — razao social"; sem grupo, so o nome de exibicao. */
    public String getSelectorLabel() {
        String display = getDisplayName();
        if (group != null && legalName != null && !legalName.equalsIgnoreCase(display)) {
            return display + " — " + legalName;
        }
        return display;
    }

    /** Empresa morta: inativa, cancelada, liquidada ou falida. */
    public boolean isDead() {
        return status != null && !"ATIVA".equals(status);
    }
}
