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
     * Todas as posições de um ativo numa carteira — os títulos dele.
     *
     * <p>É a unidade do Tesouro Direto: o saldo é do ativo, e a venda consome
     * os títulos do mais antigo para o mais novo. A nota vem junto porque é
     * dela que sai a data de compra, que ordena o FIFO.</p>
     */
    @Query("""
        SELECT pa FROM PortfolioAsset pa
        JOIN FETCH pa.asset
        LEFT JOIN FETCH pa.note
        WHERE pa.portfolio.id = :portfolioId
          AND pa.asset.id = :assetId
        """)
    List<PortfolioAsset> findAllByPortfolioIdAndAssetId(@Param("portfolioId") UUID portfolioId,
                                                        @Param("assetId") Long assetId);

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

    /**
     * Uma posição, escopada pelo dono. É o padrão de findOwnedById do
     * TransactionRepository: o filtro pelo usuário vai na consulta, não numa
     * comparação depois de carregar — a linha alheia não sai do banco.
     *
     * <p>O tipo do ativo vem junto porque quem chama decide pela coluna
     * fixed_income (só renda fixa compõe reserva de emergência), e buscá-lo
     * depois seria mais uma consulta.</p>
     */
    @Query("""
        SELECT pa FROM PortfolioAsset pa
        JOIN FETCH pa.asset a
        JOIN FETCH a.assetType
        WHERE pa.id = :id
          AND pa.portfolio.user.id = :userId
        """)
    Optional<PortfolioAsset> findOwnedById(@Param("userId") UUID userId, @Param("id") UUID id);

    /** Todas as posições, com o ativo. Usado para regravar a valorização na subida. */
    @Query("SELECT pa FROM PortfolioAsset pa JOIN FETCH pa.asset")
    List<PortfolioAsset> findAllWithAsset();
}
