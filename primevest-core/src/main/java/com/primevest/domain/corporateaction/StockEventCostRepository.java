package com.primevest.domain.corporateaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockEventCostRepository extends JpaRepository<StockEventCost, StockEventCost.Key> {
    List<StockEventCost> findByPortfolioAssetIdIn(Collection<UUID> portfolioAssetIds);

    List<StockEventCost> findByPortfolioAssetId(UUID portfolioAssetId);
}
