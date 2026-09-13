package com.primevest.domain.dividend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    List<Dividend> findByPortfolioAssetIdOrderByExDateDesc(UUID portfolioAssetId);
    List<Dividend> findByAssetIdOrderByExDateDesc(Long assetId);

    @Query("SELECT SUM(d.totalAmount) FROM Dividend d WHERE d.portfolioAsset.portfolio.id = :portfolioId")
    BigDecimal sumTotalDividendsByPortfolioId(@Param("portfolioId") UUID portfolioId);
}
