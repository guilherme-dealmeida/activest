package com.activest.domain.fund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InvestmentFundNameRepository extends JpaRepository<InvestmentFundName, Long> {

    List<InvestmentFundName> findByAssetId(Long assetId);

    @Modifying
    @Query("DELETE FROM InvestmentFundName n WHERE n.assetId = :assetId")
    void deleteByAssetId(Long assetId);
}
