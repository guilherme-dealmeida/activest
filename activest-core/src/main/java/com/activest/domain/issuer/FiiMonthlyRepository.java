package com.activest.domain.issuer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FiiMonthlyRepository extends JpaRepository<FiiMonthly, FiiMonthly.Key> {

    List<FiiMonthly> findByIssuerIdOrderByReferenceMonth(Long issuerId);

    List<FiiMonthly> findByIssuerIdAndReferenceMonthGreaterThanEqualOrderByReferenceMonth(Long issuerId,
                                                                                         LocalDate from);
}
