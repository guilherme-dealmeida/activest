package com.primevest.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByPortfolioAssetIdOrderByTransactionDateDesc(UUID portfolioAssetId);
    List<Transaction> findByPortfolioAssetPortfolioIdOrderByTransactionDateDesc(UUID portfolioId);

    /** As partes de uma mesma venda do usuario. */
    List<Transaction> findBySaleGroupId(UUID saleGroupId);

    /**
     * Transacao pelo id, mas so se ela for do usuario.
     *
     * O filtro esta na consulta, e nao numa comparacao depois de carregar: o
     * dono da transacao fica a tres saltos daqui
     * (transacao -> posicao -> carteira -> usuario), e carregar a linha para so
     * entao descobrir que ela e de outro e trabalho a mais em cima de um dado
     * que nao deveria ter saido do banco.
     *
     * Os FETCH sao os mesmos da listagem porque o DTO precisa das mesmas
     * associacoes.
     */
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.portfolioAsset pa
        JOIN FETCH pa.asset a
        JOIN FETCH a.assetType
        LEFT JOIN FETCH a.assetCategory
        LEFT JOIN FETCH t.note
        WHERE t.id = :txId
          AND pa.portfolio.user.id = :userId
        """)
    Optional<Transaction> findOwnedById(@Param("userId") UUID userId, @Param("txId") UUID txId);

    /**
     * O titulo entra no FETCH porque o DTO passou a leva-lo: sem isso, montar a
     * lista dispara uma consulta por transacao de renda fixa. LEFT porque renda
     * variavel nao tem papel — um JOIN comum sumiria com as acoes da lista.
     */
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.portfolioAsset pa
        JOIN FETCH pa.asset a
        JOIN FETCH a.assetType
        LEFT JOIN FETCH a.assetCategory
        LEFT JOIN FETCH t.note
        LEFT JOIN FETCH t.connection
        WHERE pa.portfolio.user.id = :userId
        """)
    List<Transaction> findAllTransactionsByUserId(@Param("userId") UUID userId);

    /**
     * Todas as transacoes de um ATIVO dentro de uma carteira, de todos os
     * titulos dele.
     *
     * <p>E a unidade de trabalho do Tesouro Direto: o saldo e do ativo e a
     * reparticao das vendas entre titulos e refeita por inteiro a cada escrita,
     * entao o recalculo precisa enxergar o ativo, e nao uma posicao.</p>
     */
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.portfolioAsset pa
        JOIN FETCH pa.asset a
        JOIN FETCH a.assetType
        LEFT JOIN FETCH a.assetCategory
        LEFT JOIN FETCH pa.note
        LEFT JOIN FETCH t.note
        WHERE pa.portfolio.id = :portfolioId
          AND a.id = :assetId
        """)
    List<Transaction> findByPortfolioIdAndAssetId(@Param("portfolioId") UUID portfolioId,
                                                  @Param("assetId") Long assetId);

    /** Data da transacao mais antiga do ativo, de qualquer carteira. Nula = nenhuma. */
    /**
     * Lancamentos MANUAIS do usuario que podem ser a mesma operacao de uma
     * importada (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.6): mesmos ativos, no intervalo de
     * datas do lote. A comparacao fina (tipo, data, quantidade) e do pipeline.
     */
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.portfolioAsset pa
        JOIN FETCH pa.asset a
        WHERE pa.portfolio.user.id = :userId
          AND t.origin = :origin
          AND a.id IN :assetIds
          AND t.transactionDate BETWEEN :from AND :to
        """)
    List<Transaction> findByOriginForAssets(@Param("userId") UUID userId,
                                            @Param("origin") TransactionOrigin origin,
                                            @Param("assetIds") Collection<Long> assetIds,
                                            @Param("from") java.time.LocalDate from,
                                            @Param("to") java.time.LocalDate to);

    /**
     * Dos ids que a fonte devolveu, quais esta conexao ja importou (P4). Uma
     * consulta por sincronizacao, em vez de uma por operacao.
     */
    @Query("SELECT t.externalId FROM Transaction t WHERE t.connection.id = :connectionId AND t.externalId IN :externalIds")
    Set<String> findImportedIds(@Param("connectionId") UUID connectionId,
                                @Param("externalIds") Collection<String> externalIds);

    @Query("SELECT MIN(t.transactionDate) FROM Transaction t WHERE t.portfolioAsset.asset.id = :assetId")
    java.time.LocalDate findEarliestDateByAssetId(@Param("assetId") Long assetId);
}
