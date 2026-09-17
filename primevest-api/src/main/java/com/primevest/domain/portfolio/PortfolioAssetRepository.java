package com.primevest.domain.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, UUID> {
    List<PortfolioAsset> findByPortfolioId(UUID portfolioId);

    /**
     * Posição de renda variável: sem papel, um ativo tem uma linha só.
     * Separado da busca por nota porque {@code note_id = null} nunca casa em
     * SQL — comparar com nulo dá nulo, não verdadeiro.
     */
    Optional<PortfolioAsset> findByPortfolioIdAndAssetIdAndNoteIsNull(UUID portfolioId, Long assetId);

    /** Posição de um papel específico. Dois CDBs do mesmo ativo, duas linhas. */
    Optional<PortfolioAsset> findByPortfolioIdAndNoteId(UUID portfolioId, UUID noteId);

    /**
     * A nota vem junto porque a carteira agora repete ticker: sem emissor e
     * vencimento na tela, duas linhas "CDB" ficam indistinguíveis. Buscá-la
     * depois, linha a linha, seria uma consulta por posição.
     */
    @Query("""
        SELECT pa FROM PortfolioAsset pa
        JOIN FETCH pa.asset
        LEFT JOIN FETCH pa.note
        WHERE pa.portfolio.id = :portfolioId
        """)
    List<PortfolioAsset> findAllByPortfolioIdWithAssetEagerly(@Param("portfolioId") UUID portfolioId);

    /**
     * Posições de um conjunto de ativos, de todas as carteiras. Usado para
     * regravar a valorização depois de uma atualização de cotação.
     */
    @Query("""
        SELECT pa FROM PortfolioAsset pa
        JOIN FETCH pa.asset a
        WHERE a.id IN :assetIds
        """)
    List<PortfolioAsset> findAllByAssetIdIn(@Param("assetIds") Collection<Long> assetIds);

    /** Todas as posições, com o ativo. Usado para regravar a valorização na subida. */
    @Query("SELECT pa FROM PortfolioAsset pa JOIN FETCH pa.asset")
    List<PortfolioAsset> findAllWithAsset();
}
