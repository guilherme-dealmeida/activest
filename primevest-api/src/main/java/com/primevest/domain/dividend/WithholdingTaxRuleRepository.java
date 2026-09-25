package com.primevest.domain.dividend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithholdingTaxRuleRepository extends JpaRepository<WithholdingTaxRule, Long> {
}
