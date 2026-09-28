package com.primevest.domain.corporateaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetStockEventRepository extends JpaRepository<AssetStockEvent, Long> {

    List<AssetStockEvent> findByAssetIdOrderByDataComAsc(Long assetId);

    @Query("SELECT e FROM AssetStockEvent e WHERE e.asset.id IN :assetIds ORDER BY e.dataCom ASC, e.id ASC")
    List<AssetStockEvent> findByAssetIds(@Param("assetIds") Collection<Long> assetIds);

    Optional<AssetStockEvent> findByAssetIdAndTypeAndDataComAndFactor(Long assetId, StockEventType type,
                                                                     LocalDate dataCom, BigDecimal factor);
}
