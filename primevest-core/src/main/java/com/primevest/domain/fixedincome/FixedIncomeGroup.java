package com.primevest.domain.fixedincome;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Grupo (conglomerado prudencial do BCB) das empresas de renda fixa: "Nubank" = Nu Financeira + Nu Investimentos
 * + Nu Pagamentos (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R9 e R10).
 *
 * <p>O nome do grupo e o que as telas mostram no lugar da razao social. {@code MANUAL} e curadoria e a importacao
 * nunca o sobrescreve; {@code AUTOMATICO} e o nome do BCB sem as siglas societarias e acompanha a fonte.</p>
 */
@Entity
@Table(name = "fixed_income_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedIncomeGroup {

    public enum NameSource { MANUAL, AUTOMATICO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Conglomerado prudencial do IF.data (ex.: C0084693). Nulo num grupo so de curadoria. */
    @Column(name = "bcb_code", length = 10, unique = true)
    private String bcbCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "name_source", nullable = false, length = 10)
    private NameSource nameSource;

    private String description;

    @Column(name = "leader_cnpj_root", length = 8)
    private String leaderCnpjRoot;

    /** Ticker do catalogo cujo logo o grupo usa (curadoria). */
    @Column(name = "logo_ticker", length = 20)
    private String logoTicker;

    /** So existe em bases antigas do BCB: mantido para as empresas mortas que eram dele. */
    @Column(nullable = false)
    @Builder.Default
    private boolean extinct = false;

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
}
