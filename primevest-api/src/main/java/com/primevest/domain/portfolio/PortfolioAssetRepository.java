package com.primevest.domain.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, UUID> {
    List<PortfolioAsset> findByPortfolioId(UUID portfolioId);
    Optional<PortfolioAsset> findByPortfolioIdAndAssetId(UUID portfolioId, Long assetId);

    @Query("SELECT pa FROM PortfolioAsset pa JOIN FETCH pa.asset WHERE pa.portfolio.id = :portfolioId")
    List<PortfolioAsset> findAllByPortfolioIdWithAssetEagerly(@Param("portfolioId") UUID portfolioId);
}
