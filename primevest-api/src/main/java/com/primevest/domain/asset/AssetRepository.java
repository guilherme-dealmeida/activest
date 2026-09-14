package com.primevest.domain.asset;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByTicker(String ticker);
    List<Asset> findByAssetTypeId(Long assetTypeId);
    List<Asset> findByAssetCategoryId(Long categoryId);
    List<Asset> findByActiveTrue();

    /**
     * Busca por ticker ou nome, ordenada por relevância e paginada.
     *
     * A ordenação não é enfeite: como o resultado é truncado, sem ORDER BY o
     * PostgreSQL devolve na ordem física da tabela — a ordem de inserção dos
     * seeds — e o corte descarta linhas arbitrárias. Era por isso que buscar
     * "SELIC" trazia os títulos de 2005 a 2024 e escondia o Tesouro Selic 2031.
     *
     * Três critérios, nesta ordem:
     *   1. casamento exato de ticker, depois começo de ticker, depois o resto
     *   2. títulos já vencidos por último — quem digita "SELIC" quer o que
     *      ainda existe, não a série liquidada em 2005
     *   3. ticker, para a ordem ser estável entre chamadas iguais
     */
    @Query("""
        SELECT a FROM Asset a
        WHERE LOWER(a.ticker) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(a.name)   LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY
          CASE WHEN LOWER(a.ticker) = LOWER(:query)                  THEN 0
               WHEN LOWER(a.ticker) LIKE LOWER(CONCAT(:query, '%'))  THEN 1
               WHEN LOWER(a.name)   LIKE LOWER(CONCAT(:query, '%'))  THEN 2
               ELSE 3 END,
          CASE WHEN a.maturityDate IS NOT NULL AND a.maturityDate < CURRENT_DATE THEN 1 ELSE 0 END,
          a.ticker
        """)
    List<Asset> searchByTickerOrName(@Param("query") String query, Pageable pageable);

    /** Autocomplete do cadastro de transação: o tipo escolhido restringe a lista. */
    @Query("""
        SELECT a FROM Asset a
        WHERE a.assetType.id = :typeId
          AND (LOWER(a.ticker) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(a.name)   LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY
          CASE WHEN LOWER(a.ticker) = LOWER(:query)                  THEN 0
               WHEN LOWER(a.ticker) LIKE LOWER(CONCAT(:query, '%'))  THEN 1
               WHEN LOWER(a.name)   LIKE LOWER(CONCAT(:query, '%'))  THEN 2
               ELSE 3 END,
          CASE WHEN a.maturityDate IS NOT NULL AND a.maturityDate < CURRENT_DATE THEN 1 ELSE 0 END,
          a.ticker
        """)
    List<Asset> searchByTypeAndTickerOrName(@Param("typeId") Long typeId, @Param("query") String query, Pageable pageable);
}
