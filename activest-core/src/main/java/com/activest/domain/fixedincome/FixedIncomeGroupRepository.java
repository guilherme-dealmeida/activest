package com.activest.domain.fixedincome;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FixedIncomeGroupRepository extends JpaRepository<FixedIncomeGroup, Long> {

    Optional<FixedIncomeGroup> findByBcbCode(String bcbCode);

    /** Raizes das lideres dos grupos: a rotina de logos procura tambem por elas (Adendo A do plano de emissoras). */
    @org.springframework.data.jpa.repository.Query(
        "select distinct g.leaderCnpjRoot from FixedIncomeGroup g where g.leaderCnpjRoot is not null")
    java.util.List<String> findAllLeaderRoots();
}
