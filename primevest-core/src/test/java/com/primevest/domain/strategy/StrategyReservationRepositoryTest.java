package com.primevest.domain.strategy;

import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.support.Fixtures;
import com.primevest.support.JpaTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyReservationRepositoryTest extends JpaTestBase {

    @Autowired private StrategyReservationRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private User owner;

    @BeforeEach
    void scenario() {
        owner = userRepository.save(Fixtures.user("dono@primevest.test"));
    }

    @Test
    @DisplayName("unique(user_id): a reserva e uma por usuario")
    void onePerUser() {
        repository.saveAndFlush(Fixtures.reservationByPercentage(owner, "30.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(Fixtures.reservationByPercentage(owner, "40.00")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ReservationTypeConverter grava 1 para PERCENTAGE e 2 para VALUE, e le de volta o enum")
    void typeConverter() {
        StrategyReservation percentage = repository.saveAndFlush(Fixtures.reservationByPercentage(owner, "30.00"));
        em.clear();

        Number stored = (Number) em.createNativeQuery(
                "SELECT type_reservation FROM strategy_reservations WHERE id = :id")
            .setParameter("id", percentage.getId())
            .getSingleResult();
        assertThat(stored.shortValue()).isEqualTo((short) 1);
        assertThat(repository.findByUserId(owner.getId()))
            .get().extracting(StrategyReservation::getTypeReservation).isEqualTo(ReservationType.PERCENTAGE);

        User other = userRepository.save(Fixtures.user("outro@primevest.test"));
        StrategyReservation byValue = repository.saveAndFlush(Fixtures.reservationByValue(other, "30000.00"));
        em.clear();

        Number storedValue = (Number) em.createNativeQuery(
                "SELECT type_reservation FROM strategy_reservations WHERE id = :id")
            .setParameter("id", byValue.getId())
            .getSingleResult();
        assertThat(storedValue.shortValue()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("ck_strategy_reservations_target: PERCENTAGE sem percentual e recusado pelo banco")
    void percentageRequiredInPercentageType() {
        StrategyReservation withoutTarget = Fixtures.reservationByPercentage(owner, "30.00");
        withoutTarget.setTargetPercentage(null);

        assertThatThrownBy(() -> repository.saveAndFlush(withoutTarget))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_strategy_reservations_target");
    }

    @Test
    @DisplayName("ck_strategy_reservations_target: as duas colunas preenchidas ao mesmo tempo e recusado")
    void bothTargetsTogetherFail() {
        StrategyReservation both = Fixtures.reservationByPercentage(owner, "30.00");
        both.setTargetValue(new BigDecimal("30000.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(both))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_strategy_reservations_target");
    }

    @Test
    @DisplayName("ck_strategy_reservations_target: percentual acima de 100 e recusado pelo banco")
    void percentageAboveHundred() {
        StrategyReservation above = Fixtures.reservationByPercentage(owner, "100.01");

        assertThatThrownBy(() -> repository.saveAndFlush(above))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_strategy_reservations_target");
    }

    @Test
    @DisplayName("VALUE com valor alvo e sem percentual passa")
    void validValueType() {
        StrategyReservation byValue = repository.saveAndFlush(Fixtures.reservationByValue(owner, "30000.00"));

        assertThat(byValue.getTargetPercentage()).isNull();
        assertThat(byValue.getTargetValue()).isEqualByComparingTo("30000.00");
    }
}
