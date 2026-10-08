package com.primevest.domain.marketindex;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketIndexRepository extends JpaRepository<MarketIndex, Long> {

    Optional<MarketIndex> findByCode(String code);

    List<MarketIndex> findByActiveTrueOrderByIdAsc();
}
