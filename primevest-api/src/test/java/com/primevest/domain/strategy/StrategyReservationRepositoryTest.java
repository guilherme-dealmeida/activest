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

    private User dono;

    @BeforeEach
    void cenario() {
        dono = userRepository.save(Fixtures.user("dono@primevest.test"));
    }

    @Test
    @DisplayName("unique(user_id): a reserva e uma por usuario")
    void umaPorUsuario() {
        repository.saveAndFlush(Fixtures.reservationByPercentage(dono, "30.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(Fixtures.reservationByPercentage(dono, "40.00")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ReservationTypeConverter grava 1 para PERCENTAGE e 2 para VALUE, e le de volta o enum")
    void conversorDoTipo() {
        StrategyReservation percentual = repository.saveAndFlush(Fixtures.reservationByPercentage(dono, "30.00"));
        em.clear();

        Number gravado = (Number) em.createNativeQuery(
                "SELECT type_reservation FROM strategy_reservations WHERE id = :id")
            .setParameter("id", percentual.getId())
            .getSingleResult();
        assertThat(gravado.shortValue()).isEqualTo((short) 1);
        assertThat(repository.findByUserId(dono.getId()))
            .get().extracting(StrategyReservation::getTypeReservation).isEqualTo(ReservationType.PERCENTAGE);

        User outro = userRepository.save(Fixtures.user("outro@primevest.test"));
        StrategyReservation porValor = repository.saveAndFlush(Fixtures.reservationByValue(outro, "30000.00"));
        em.clear();

        Number gravadoValor = (Number) em.createNativeQuery(
                "SELECT type_reservation FROM strategy_reservations WHERE id = :id")
            .setParameter("id", porValor.getId())
            .getSingleResult();
        assertThat(gravadoValor.shortValue()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("ck_strategy_reservations_target: PERCENTAGE sem percentual e recusado pelo banco")
    void percentualObrigatorioNoTipoPercentual() {
        StrategyReservation semAlvo = Fixtures.reservationByPercentage(dono, "30.00");
        semAlvo.setTargetPercentage(null);

        assertThatThrownBy(() -> repository.saveAndFlush(semAlvo))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_strategy_reservations_target");
    }

    @Test
    @DisplayName("ck_strategy_reservations_target: as duas colunas preenchidas ao mesmo tempo e recusado")
    void osDoisAlvosJuntosNaoPassam() {
        StrategyReservation ambos = Fixtures.reservationByPercentage(dono, "30.00");
        ambos.setTargetValue(new BigDecimal("30000.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(ambos))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_strategy_reservations_target");
    }

    @Test
    @DisplayName("ck_strategy_reservations_target: percentual acima de 100 e recusado pelo banco")
    void percentualAcimaDeCem() {
        StrategyReservation acima = Fixtures.reservationByPercentage(dono, "100.01");

        assertThatThrownBy(() -> repository.saveAndFlush(acima))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_strategy_reservations_target");
    }

    @Test
    @DisplayName("VALUE com valor alvo e sem percentual passa")
    void tipoValorValido() {
        StrategyReservation porValor = repository.saveAndFlush(Fixtures.reservationByValue(dono, "30000.00"));

        assertThat(porValor.getTargetPercentage()).isNull();
        assertThat(porValor.getTargetValue()).isEqualByComparingTo("30000.00");
    }
}
