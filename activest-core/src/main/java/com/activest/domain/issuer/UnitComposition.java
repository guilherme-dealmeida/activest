package com.activest.domain.issuer;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Composicao de uma unit (V28): KLBN11 = 1 ON + 4 PN. Uma linha por classe.
 * Vem do FCA (F3) ou da ligacao manual.
 */
@Entity
@Table(name = "unit_compositions")
@IdClass(UnitComposition.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitComposition {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "share_class", length = 10)
    private ShareClass shareClass;

    @Column(nullable = false)
    private Integer quantity;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long assetId;
        private ShareClass shareClass;
    }
}
