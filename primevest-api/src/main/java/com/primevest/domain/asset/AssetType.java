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

    @JsonIgnore
    @OneToMany(mappedBy = "assetType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssetCategory> assetCategories = new ArrayList<>();
}
