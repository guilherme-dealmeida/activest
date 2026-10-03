package com.primevest.domain.asset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "asset_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "color_hex")
    private String colorHex;

    /** Provedor de cotação deste tipo. Ver {@link PriceSource}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", nullable = false)
    @Builder.Default
    private PriceSource priceSource = PriceSource.MANUAL;

    /** Tipos com remuneração contratada (rate_type, indexer, indexer_rate em Asset). */
    @Column(name = "fixed_income", nullable = false)
    @Builder.Default
    private boolean fixedIncome = false;

    /**
     * Tipos lancados por VALOR TOTAL, sem quantidade nem preco unitario. Hoje so
     * RENDA_FIXA: um CDB, uma LCI ou uma debenture comprados no banco tem valor
     * aplicado, nao "N cotas a R$ X". Tesouro Direto fica de fora de proposito,
     * porque o titulo e negociado em fracoes com preco unitario publicado.
     *
     * <p>Comparacao por nome concentrada neste unico ponto. Se outro tipo passar
     * a ter a mesma caracteristica, o lugar certo e uma coluna em asset_types,
     * como price_source e fixed_income.</p>
     */
    public boolean isValuedByTotal() {
        return "RENDA_FIXA".equals(name);
    }

    /**
     * Tesouro Direto. Tem titulo como a Renda Fixa, mas o saldo e por COTA e do
     * ATIVO, nao do titulo: a venda e do ativo e consome os titulos mais
     * antigos primeiro (FIFO). E o unico tipo com esse comportamento.
     *
     * <p>Mesma ressalva do {@link #isValuedByTotal()} sobre comparar por nome:
     * se surgir outro tipo assim, a caracteristica vira coluna em asset_types.</p>
     */
    public boolean isTreasury() {
        return "TESOURO_DIRETO".equals(name);
    }

    /**
     * Renda fixa com titulo informado pelo usuario (CDB, LCI, debenture...). O
     * Tesouro tambem tem titulo, mas ele nasce de cada compra e nunca e
     * escolhido a mao.
     */
    public boolean isUserNote() {
        return fixedIncome && !isTreasury();
    }

    /**
     * Criptomoeda: quantidade e preco com ate 8 casas (a escala de
     * {@code numeric(18,8)} existe por causa dela). A tela usa para decidir a
     * mascara e a exibicao; o calculo e o mesmo dos demais tipos por cota.
     * Mesma ressalva do {@link #isValuedByTotal()} sobre comparar por nome.
     */
    public boolean isCrypto() {
        return "CRIPTO".equals(name);
    }

    @JsonIgnore
    @OneToMany(mappedBy = "assetType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssetCategory> assetCategories = new ArrayList<>();
}
