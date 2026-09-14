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

    @JsonIgnore
    @OneToMany(mappedBy = "assetType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssetCategory> assetCategories = new ArrayList<>();
}
