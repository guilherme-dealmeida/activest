package com.primevest.domain.asset;

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

    /**
     * Segundo nível da classificação, abaixo de name/description.
     * FII: segmento de atuação ('Lajes Corporativas'), com name = tipo de fundo.
     * Ação: segmento da B3 ('Bancos'), com name = setor econômico.
     * Nulo nos demais tipos.
     */
    private String segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    @JsonIgnore
    @OneToMany(mappedBy = "assetCategory", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Asset> assets = new ArrayList<>();

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
