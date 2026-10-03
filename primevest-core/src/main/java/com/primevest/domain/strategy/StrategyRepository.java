package com.primevest.domain.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
/**
 * Sem exclusao por tipo: a estrategia so muda pelo PUT com a lista completa,
 * que e o que garante a soma de 100%. Apagar um tipo sozinho deixava a
 * estrategia somando menos que isso.
 */
public interface StrategyRepository extends JpaRepository<Strategy, UUID> {
    List<Strategy> findByUserId(UUID userId);
    Optional<Strategy> findByUserIdAndAssetTypeId(UUID userId, Long assetTypeId);
}
