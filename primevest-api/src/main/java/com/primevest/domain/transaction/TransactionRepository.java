package com.primevest.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByPortfolioAssetIdOrderByTransactionDateDesc(UUID portfolioAssetId);
    List<Transaction> findByPortfolioAssetPortfolioIdOrderByTransactionDateDesc(UUID portfolioId);

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
        LEFT JOIN FETCH t.note
        WHERE pa.portfolio.user.id = :userId
        """)
    List<Transaction> findAllTransactionsByUserId(@Param("userId") UUID userId);
}
