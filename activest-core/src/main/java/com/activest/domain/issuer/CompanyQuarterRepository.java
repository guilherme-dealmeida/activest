package com.activest.domain.issuer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompanyQuarterRepository extends JpaRepository<CompanyQuarter, CompanyQuarter.Key> {

    List<CompanyQuarter> findByIssuerIdOrderByPeriodEnd(Long issuerId);

    List<CompanyQuarter> findByIssuerIdAndPeriodEndGreaterThanEqualOrderByPeriodEnd(Long issuerId, LocalDate from);
}
