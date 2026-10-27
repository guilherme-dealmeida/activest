package com.activest.domain.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {

    Optional<Currency> findByBaseTrue();

    List<Currency> findAllByOrderByBaseDescCodeAsc();

    List<Currency> findByActiveTrueAndRateSourceOrderByCode(CurrencyRateSource source);
}
