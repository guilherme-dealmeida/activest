package com.activest.domain.marketindex;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketIndexValueRepository extends JpaRepository<MarketIndexValue, MarketIndexValue.Key> {

    List<MarketIndexValue> findByIndexIdAndReferenceDateBetweenOrderByReferenceDate(Long indexId, LocalDate from,
                                                                                   LocalDate to);

    /** Ultimo valor ate a data (inclusive): base e fim da variacao de um indice de nivel. */
    Optional<MarketIndexValue> findFirstByIndexIdAndReferenceDateLessThanEqualOrderByReferenceDateDesc(Long indexId,
                                                                                                      LocalDate date);

    Optional<MarketIndexValue> findFirstByIndexIdOrderByReferenceDateDesc(Long indexId);

    Optional<MarketIndexValue> findFirstByIndexIdOrderByReferenceDateAsc(Long indexId);

    long countByIndexId(Long indexId);
}
