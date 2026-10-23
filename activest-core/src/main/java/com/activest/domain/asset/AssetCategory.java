package com.activest.domain.asset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "asset_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    /**
     * Renda fixa: tipos de empresa aceitos como emissora deste produto, separados por virgula (ex.: "BANCO" no CDB;
     * docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 4.4). Nulo fora da renda fixa e do Tesouro.
     */
    @Column(name = "issuer_kinds", length = 120)
    private String issuerKinds;

    /** Renda fixa: o produto esta entre os creditos cobertos pelo FGC/FGCoop (R11). */
    @Column(nullable = false)
    @Builder.Default
    private boolean guaranteed = false;

    @JsonIgnore
    @OneToMany(mappedBy = "assetCategory", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Asset> assets = new ArrayList<>();

    /** {@link #issuerKinds} como lista; vazia quando nulo. */
    public java.util.List<com.activest.domain.fixedincome.FixedIncomeIssuerKind> getAcceptedIssuerKinds() {
        if (issuerKinds == null || issuerKinds.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(issuerKinds.split(","))
                .map(String::trim).filter(k -> !k.isEmpty())
                .map(com.activest.domain.fixedincome.FixedIncomeIssuerKind::valueOf)
                .toList();
    }

    /**
     * Rótulo para exibição: a descrição quando existe, senão o nome técnico.
     *
     * name é a chave usada nas migrations e nos scripts ('LAJES_CORPORATIVAS');
     * description é o que uma pessoa lê ('Lajes Corporativas'). Toda tela deve
     * usar este método — as três que mostravam categoria vinham exibindo a
     * chave, enquanto o tipo de ativo ao lado já mostrava a descrição.
     *
     * Não é propriedade mapeada: a entidade usa acesso por campo (a anotação
     * @Id está no campo), então o Hibernate ignora getters extras.
     */
    public String getDisplayName() {
        return description != null && !description.isBlank() ? description : name;
    }
}
