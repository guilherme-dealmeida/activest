package com.primevest.domain.issuer;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Emissor de ativos do catalogo: companhia aberta ou FII (V28,
 * PLANO_EMPRESAS_BALANCOS.md). Global, sem dono: o balanco da Petrobras e o
 * mesmo para todo usuario.
 *
 * <p>Colunas de companhia e de FII convivem, nulas no outro tipo. O segmento
 * curado do FII ({@link #segment}) vem do seed de emissores e a importacao nunca
 * o sobrescreve; o da CVM fica em {@link #cvmSegment}, so como informacao.</p>
 */
@Entity
@Table(name = "issuers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issuer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IssuerKind kind;

    /** So digitos, 14 posicoes. */
    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    /** Codigo CVM da companhia. Nulo em FII. */
    @Column(name = "cvm_code", unique = true)
    private Integer cvmCode;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    /** ATIVO / CANCELADA (cadastro da CVM). */
    @Column(name = "registration_status", length = 20)
    private String registrationStatus;

    private String website;

    // --- COMPANY ---

    @Column(name = "cvm_sector", length = 120)
    private String cvmSector;

    @Column(name = "b3_sector", length = 120)
    private String b3Sector;

    @Column(name = "b3_subsector", length = 120)
    private String b3Subsector;

    @Column(name = "b3_segment", length = 120)
    private String b3Segment;

    @Column(name = "registration_category", length = 20)
    private String registrationCategory;

    @Column(name = "listing_segment", length = 80)
    private String listingSegment;

    @Column(name = "fiscal_year_end_month")
    private Short fiscalYearEndMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_layout", length = 20)
    private StatementLayout statementLayout;

    // --- FII ---

    /** Segmento curado (seed). A importacao nunca escreve aqui. */
    @Column(length = 120)
    private String segment;

    @Column(name = "cvm_segment", length = 120)
    private String cvmSegment;

    @Column(name = "administrator_name")
    private String administratorName;

    @Column(name = "administrator_cnpj", length = 14)
    private String administratorCnpj;

    @Column(name = "management_type", length = 20)
    private String managementType;

    /** Data de referencia do documento da fonte que atualizou o emissor por ultimo. */
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
}
