package com.activest.domain.asset;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    // findByTicker saiu na V13: o ticker deixou de ser único no catálogo (é único por tipo entre os listados). A busca
    // por ticker passa pelo AssetResolver, que exige o tipo (ou os tipos da fonte) — docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, D3.

    /** Todos os ativos com este ticker: homônimos de tipos diferentes e deslistados do mesmo tipo (V13). */
    @Query("SELECT a FROM Asset a JOIN FETCH a.assetType LEFT JOIN FETCH a.terms WHERE a.ticker = :ticker")
    List<Asset> findAllByTicker(@Param("ticker") String ticker);

    /** Os ativos com este ticker nos tipos dados (nomes de {@code asset_types}). */
    @Query("SELECT a FROM Asset a JOIN FETCH a.assetType t LEFT JOIN FETCH a.terms WHERE a.ticker = :ticker AND t.name IN :types")
    List<Asset> findAllByTickerInTypes(@Param("ticker") String ticker,
                                       @Param("types") java.util.Collection<String> types);

    /** Ativos dos tipos dados, com o tipo carregado — base dos mapas ticker → ativo de uma fonte (COTAHIST, B3). */
    @Query("SELECT a FROM Asset a JOIN FETCH a.assetType t WHERE t.name IN :types")
    List<Asset> findAllInTypes(@Param("types") java.util.Collection<String> types);

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
     * Ativo inativo (deslistado — V14) não aparece: a busca serve para lançar
     * transação nova, e papel que saiu de negociação não recebe compra. A
     * posição que já existe nele continua na carteira.
     *
     * O vencimento agora vem de asset_terms. O LEFT JOIN FETCH serve às duas
     * coisas de uma vez: alimenta o critério 2 e evita o SELECT por linha na
     * hora de montar o DTO.
     */
    @Query("""
        SELECT a FROM Asset a
        LEFT JOIN FETCH a.terms t
        WHERE a.active = true
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
    List<Asset> searchByTickerOrName(@Param("query") String query, Pageable pageable);

    /**
     * Autocomplete do cadastro de transação: o tipo escolhido restringe a lista. {@code includeInactive}: só a Nova
     * transação traz o papel que saiu da bolsa (D11 — lançamento retroativo), depois dos listados.
     */
    @Query("""
        SELECT a FROM Asset a
        LEFT JOIN FETCH a.terms t
        WHERE a.assetType.id = :typeId
          AND (a.active = true OR :includeInactive = true)
          AND (LOWER(a.ticker) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(a.name)   LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY
          CASE WHEN LOWER(a.ticker) = LOWER(:query)                  THEN 0
               WHEN LOWER(a.ticker) LIKE LOWER(CONCAT(:query, '%'))  THEN 1
               WHEN LOWER(a.name)   LIKE LOWER(CONCAT(:query, '%'))  THEN 2
               ELSE 3 END,
          CASE WHEN t.maturityDate IS NOT NULL AND t.maturityDate < CURRENT_DATE THEN 1 ELSE 0 END,
          CASE WHEN a.active = true THEN 0 ELSE 1 END,
          a.ticker,
          a.delistedOn DESC NULLS FIRST
        """)
    List<Asset> searchByTypeAndTickerOrName(@Param("typeId") Long typeId, @Param("query") String query,
                                            @Param("includeInactive") boolean includeInactive, Pageable pageable);

    /** Autocomplete da tela de emissoras: so ativos ligados a um emissor (acoes e FIIs). */
    @Query("""
        SELECT a FROM Asset a
        LEFT JOIN FETCH a.terms t
        WHERE a.issuer IS NOT NULL
          AND a.issuer.kind IN :kinds
          AND a.active = true
          AND (LOWER(a.ticker) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(a.name)   LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY
          CASE WHEN LOWER(a.ticker) = LOWER(:query)                  THEN 0
               WHEN LOWER(a.ticker) LIKE LOWER(CONCAT(:query, '%'))  THEN 1
               WHEN LOWER(a.name)   LIKE LOWER(CONCAT(:query, '%'))  THEN 2
               ELSE 3 END,
          a.ticker
        """)
    List<Asset> searchWithIssuerByTickerOrName(@Param("query") String query,
                                               @Param("kinds") java.util.Collection<com.activest.domain.issuer.IssuerKind> kinds,
                                               Pageable pageable);

    /** Ativos ativos com o tipo carregado — base da conferencia do catalogo. */
    @Query("SELECT a FROM Asset a JOIN FETCH a.assetType WHERE a.active = true")
    List<Asset> findActiveWithType();

    /** Todos os ativos com o tipo carregado — base da rotina de logos. */
    @Query("SELECT a FROM Asset a JOIN FETCH a.assetType")
    List<Asset> findAllWithType();
}
