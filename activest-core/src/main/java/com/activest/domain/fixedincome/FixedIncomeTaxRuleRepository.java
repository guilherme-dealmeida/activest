package com.activest.domain.fixedincome;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixedIncomeTaxRuleRepository extends JpaRepository<FixedIncomeTaxRule, Long> {
}
