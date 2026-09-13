package com.primevest.domain.strategy;

import com.primevest.domain.asset.AssetType;
import com.primevest.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "strategies", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "asset_type_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Strategy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    @Column(name = "target_percentage", precision = 5, scale = 2, nullable = false)
    private BigDecimal targetPercentage;

    @Column(name = "include_reservation", nullable = false)
    private boolean includeReservation;
}
