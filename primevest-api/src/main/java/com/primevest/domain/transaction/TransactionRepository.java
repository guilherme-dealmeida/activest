package com.primevest.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByPortfolioAssetIdOrderByTransactionDateDesc(UUID portfolioAssetId);
    List<Transaction> findByPortfolioAssetPortfolioIdOrderByTransactionDateDesc(UUID portfolioId);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.portfolioAsset pa JOIN FETCH pa.asset a JOIN FETCH a.assetType WHERE pa.portfolio.user.id = :userId")
    List<Transaction> findAllTransactionsByUserId(@Param("userId") UUID userId);
}
