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
    List<Asset> findByAssetCategoryId(Long categoryId);
    List<Asset> findByActiveTrue();

    /**
     * Todas as consultas que viram DTO trazem {@code terms} no mesmo SELECT.
     *
     * O lado inverso de um {@code @OneToOne} não é de fato preguiçoso: o
     * Hibernate precisa decidir entre o objeto e null, então dispara um SELECT
     * por linha se a associação não vier junto. Numa lista de 1249 ativos isso
     * é 1250 consultas.
     */
    @Query("SELECT a FROM Asset a LEFT JOIN FETCH a.terms")
    List<Asset> findAllWithTerms();

    @Query("SELECT a FROM Asset a LEFT JOIN FETCH a.terms WHERE a.assetType.id = :typeId")
    List<Asset> findByAssetTypeId(@Param("typeId") Long typeId);

    @Query("SELECT a FROM Asset a LEFT JOIN FETCH a.terms WHERE a.ticker = :ticker")
    Optional<Asset> findByTickerWithTerms(@Param("ticker") String ticker);

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
     *
     * O vencimento agora vem de asset_terms. O LEFT JOIN FETCH serve às duas
     * coisas de uma vez: alimenta o critério 2 e evita o SELECT por linha na
     * hora de montar o DTO.
     */
    @Query("""
        SELECT a FROM Asset a
        LEFT JOIN FETCH a.terms t
        WHERE LOWER(a.ticker) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(a.name)   LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY
          CASE WHEN LOWER(a.ticker) = LOWER(:query)                  THEN 0
               WHEN LOWER(a.ticker) LIKE LOWER(CONCAT(:query, '%'))  THEN 1
               WHEN LOWER(a.name)   LIKE LOWER(CONCAT(:query, '%'))  THEN 2
               ELSE 3 END,
          CASE WHEN t.maturityDate IS NOT NULL AND t.maturityDate < CURRENT_DATE THEN 1 ELSE 0 END,
          a.ticker
        """)
    List<Asset> searchByTickerOrName(@Param("query") String query, Pageable pageable);

    /** Autocomplete do cadastro de transação: o tipo escolhido restringe a lista. */
    @Query("""
        SELECT a FROM Asset a
        LEFT JOIN FETCH a.terms t
        WHERE a.assetType.id = :typeId
          AND (LOWER(a.ticker) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(a.name)   LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY
          CASE WHEN LOWER(a.ticker) = LOWER(:query)                  THEN 0
               WHEN LOWER(a.ticker) LIKE LOWER(CONCAT(:query, '%'))  THEN 1
               WHEN LOWER(a.name)   LIKE LOWER(CONCAT(:query, '%'))  THEN 2
               ELSE 3 END,
          CASE WHEN t.maturityDate IS NOT NULL AND t.maturityDate < CURRENT_DATE THEN 1 ELSE 0 END,
          a.ticker
        """)
    List<Asset> searchByTypeAndTickerOrName(@Param("typeId") Long typeId, @Param("query") String query, Pageable pageable);
}
