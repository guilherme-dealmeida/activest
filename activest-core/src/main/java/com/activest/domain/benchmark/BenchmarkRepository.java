package com.activest.domain.benchmark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BenchmarkRepository extends JpaRepository<Benchmark, Long> {

    @Query("SELECT b FROM Benchmark b LEFT JOIN FETCH b.marketIndex LEFT JOIN FETCH b.asset a "
            + "LEFT JOIN FETCH a.assetType LEFT JOIN FETCH a.terms WHERE b.active = true ORDER BY b.sortOrder")
    List<Benchmark> findActive();

    Optional<Benchmark> findByCode(String code);
}
