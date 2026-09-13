package com.primevest.domain.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StrategyRepository extends JpaRepository<Strategy, UUID> {
    List<Strategy> findByUserId(UUID userId);
    Optional<Strategy> findByUserIdAndAssetTypeId(UUID userId, Long assetTypeId);
    void deleteByUserIdAndAssetTypeId(UUID userId, Long assetTypeId);
}
