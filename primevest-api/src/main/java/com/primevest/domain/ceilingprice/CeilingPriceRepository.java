package com.primevest.domain.ceilingprice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CeilingPriceRepository extends JpaRepository<CeilingPrice, UUID> {
    List<CeilingPrice> findByUserId(UUID userId);
    Optional<CeilingPrice> findByUserIdAndAssetId(UUID userId, Long assetId);
}
