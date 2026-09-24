package com.primevest.domain.dividend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    List<Dividend> findByPortfolioAssetIdOrderByExDateDesc(UUID portfolioAssetId);

    /**
     * Proventos de um ativo, restritos ao que o usuario recebeu.
     *
     * O dono de um provento e alcancado pela posicao que o gerou
     * (provento -> posicao -> carteira -> usuario). Sem esse caminho na
     * consulta, buscar por ticker devolvia os proventos de TODO mundo naquele
     * ativo — e ticker qualquer um sabe.
     *
     * O ativo tambem vem pela posicao: o provento nao tem mais asset_id
     * proprio.
     */
    @Query("""
        SELECT d FROM Dividend d
        JOIN d.portfolioAsset pa
        WHERE pa.asset.id = :assetId
          AND pa.portfolio.user.id = :userId
        ORDER BY d.exDate DESC
        """)
    List<Dividend> findByAssetIdAndOwner(@Param("userId") UUID userId, @Param("assetId") Long assetId);

    @Query("SELECT SUM(d.totalAmount) FROM Dividend d WHERE d.portfolioAsset.portfolio.id = :portfolioId")
    BigDecimal sumTotalDividendsByPortfolioId(@Param("portfolioId") UUID portfolioId);

    /**
     * Total de proventos por posicao de uma carteira, numa consulta so.
     *
     * <p>Substitui portfolio_assets.total_dividends: o total e sempre a soma dos
     * proventos da posicao, e grava-lo era uma segunda fonte que so ficava certa
     * enquanto todo caminho que cria ou apaga provento lembrasse de atualiza-la.
     * Posicao sem provento nao aparece no resultado.</p>
     */
    @Query("""
        SELECT new com.primevest.domain.dividend.PositionDividendTotal(pa.id, SUM(d.totalAmount))
        FROM Dividend d
        JOIN d.portfolioAsset pa
        WHERE pa.portfolio.id = :portfolioId
        GROUP BY pa.id
        """)
    List<PositionDividendTotal> sumTotalAmountByPosition(@Param("portfolioId") UUID portfolioId);

    /** Proventos da posicao de certos tipos (devolucoes de capital, no calculo da posicao). */
    List<Dividend> findByPortfolioAssetIdAndTypeIn(UUID portfolioAssetId, Collection<DividendType> types);

    /** Proventos de certos tipos de todas as posicoes de uma carteira, numa consulta so. */
    @Query("""
        SELECT d FROM Dividend d
        JOIN FETCH d.portfolioAsset pa
        WHERE pa.portfolio.id = :portfolioId
          AND d.type IN :types
        """)
    List<Dividend> findByPortfolioIdAndTypeIn(@Param("portfolioId") UUID portfolioId,
                                              @Param("types") Collection<DividendType> types);

    /** Proventos AUTO da posicao, com o evento do catalogo que os gerou. */
    @Query("""
        SELECT d FROM Dividend d
        LEFT JOIN FETCH d.cashEvent
        WHERE d.portfolioAsset.id = :portfolioAssetId
          AND d.origin = com.primevest.domain.dividend.DividendOrigin.AUTO
        """)
    List<Dividend> findAutoByPortfolioAssetId(@Param("portfolioAssetId") UUID portfolioAssetId);
}
