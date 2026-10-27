package com.activest.domain.fund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InvestmentFundRepository extends JpaRepository<InvestmentFund, Long> {

    List<InvestmentFund> findByAssetIdIn(Collection<Long> assetIds);
}
