package com.primevest.domain.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Sem delete exposto por nenhum service: a estrategia de reserva nao pode ser
 * apagada. O deleteById herdado de JpaRepository continua existindo, e e por
 * isso que a regra vive tambem na ausencia de endpoint, e nao so aqui.
 */
@Repository
public interface StrategyReservationRepository extends JpaRepository<StrategyReservation, UUID> {
    Optional<StrategyReservation> findByUserId(UUID userId);
}
