package com.primevest.domain.strategy;

import com.primevest.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Estrategia da reserva de emergencia do usuario.
 *
 * <p>Uma por usuario, sempre: nasce com ele (AuthService, e a V12 para o
 * usuario semeado) e nunca e apagada pela aplicacao — nao existe endpoint de
 * exclusao. A unicidade de {@code user_id} esta no banco.</p>
 *
 * <p>O alvo mora em {@code targetPercentage} OU em {@code targetValue},
 * conforme {@link #typeReservation}; a outra coluna e nula, e o banco confere
 * (ck_strategy_reservations_target).</p>
 */
@Entity
@Table(name = "strategy_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Convert(converter = ReservationTypeConverter.class)
    @Column(name = "type_reservation", nullable = false)
    private ReservationType typeReservation;

    @Column(name = "target_percentage", precision = 5, scale = 2)
    private BigDecimal targetPercentage;

    @Column(name = "target_value", precision = 18, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** O padrao do nascimento: por percentual, alvo 0,00%. */
    public static StrategyReservation defaultFor(User user) {
        return StrategyReservation.builder()
            .user(user)
            .typeReservation(ReservationType.PERCENTAGE)
            .targetPercentage(BigDecimal.ZERO.setScale(2))
            .build();
    }

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
