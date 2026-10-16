package com.primevest.domain.fixedincome;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FixedIncomeGroupRepository extends JpaRepository<FixedIncomeGroup, Long> {

    Optional<FixedIncomeGroup> findByBcbCode(String bcbCode);
}
