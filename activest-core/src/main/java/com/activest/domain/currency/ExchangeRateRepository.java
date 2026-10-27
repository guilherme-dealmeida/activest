package com.activest.domain.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, ExchangeRate.Key> {

    List<ExchangeRate> findByCurrencyCodeOrderByReferenceDate(String currencyCode);

    List<ExchangeRate> findByCurrencyCodeAndReferenceDateBetweenOrderByReferenceDate(String currencyCode, LocalDate from,
                                                                                     LocalDate to);

    Optional<ExchangeRate> findFirstByCurrencyCodeOrderByReferenceDateDesc(String currencyCode);

    Optional<ExchangeRate> findFirstByCurrencyCodeOrderByReferenceDateAsc(String currencyCode);

    long countByCurrencyCode(String currencyCode);
}
