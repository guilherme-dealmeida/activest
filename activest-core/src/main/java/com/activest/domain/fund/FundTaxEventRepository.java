package com.activest.domain.fund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FundTaxEventRepository extends JpaRepository<FundTaxEvent, Long> {

    List<FundTaxEvent> findByUserIdAndAssetIdOrderByEventDate(UUID userId, Long assetId);

    List<FundTaxEvent> findByUserId(UUID userId);

    List<FundTaxEvent> findByLotTransactionIdIn(java.util.Collection<UUID> lotTransactionIds);

    @Modifying
    @Query("DELETE FROM FundTaxEvent e WHERE e.lotTransactionId IN :lots")
    void deleteByLotTransactionIdIn(java.util.Collection<UUID> lots);

    @Modifying
    @Query("DELETE FROM FundTaxEvent e WHERE e.userId = :userId AND e.assetId = :assetId")
    void deleteByUserIdAndAssetId(UUID userId, Long assetId);
}
