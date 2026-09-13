package com.primevest.domain.pricehistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetPriceHistoryRepository extends JpaRepository<AssetPriceHistory, Long> {
    List<AssetPriceHistory> findByAssetIdAndReferenceDateBetweenOrderByReferenceDateAsc(Long assetId, LocalDate start, LocalDate end);
    Optional<AssetPriceHistory> findByAssetIdAndReferenceDate(Long assetId, LocalDate referenceDate);
}
