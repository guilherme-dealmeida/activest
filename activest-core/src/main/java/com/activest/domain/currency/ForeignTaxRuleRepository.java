package com.activest.domain.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForeignTaxRuleRepository extends JpaRepository<ForeignTaxRule, Long> {

    @Query("SELECT r FROM ForeignTaxRule r LEFT JOIN FETCH r.assetType ORDER BY r.tax, r.event, r.validFrom NULLS FIRST, r.id")
    List<ForeignTaxRule> findAllOrdered();
}
