package com.primevest.domain.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByTicker(String ticker);
    List<Asset> findByAssetTypeId(Long assetTypeId);
    List<Asset> findByAssetCategoryId(Long categoryId);
    List<Asset> findByActiveTrue();

    @Query("SELECT a FROM Asset a WHERE LOWER(a.ticker) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Asset> searchByTickerOrName(@Param("query") String query);
}
