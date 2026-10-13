package com.primevest.domain.dividend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    /**
     * Proventos de todas as posicoes de uma carteira, com posicao, ativo e tipo
     * do ativo ja carregados — a tela de proventos mostra os tres. Uma consulta
     * so, no lugar de uma por posicao mais a carga preguicosa de cada provento.
     */
    @Query("""
        SELECT d FROM Dividend d
        JOIN FETCH d.portfolioAsset pa
        JOIN FETCH pa.asset a
        JOIN FETCH a.assetType
        LEFT JOIN FETCH d.withholdingRule
        LEFT JOIN FETCH d.cashEvent
        WHERE pa.portfolio.id = :portfolioId
        ORDER BY d.exDate DESC, d.id DESC
        """)
    List<Dividend> findByPortfolioIdWithAsset(@Param("portfolioId") UUID portfolioId);

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

    /**
     * Soma LIQUIDA do que ja foi RECEBIDO ate {@code today} (bruto onde o tipo
     * nao tem regra de IR). Mesma regra de {@link Dividend#isReceived}.
     */
    @Query("""
        SELECT SUM(COALESCE(d.netAmount, d.totalAmount)) FROM Dividend d
        LEFT JOIN d.cashEvent ce
        WHERE d.portfolioAsset.portfolio.id = :portfolioId
          AND (d.paymentDate <= :today
               OR (d.paymentDate IS NULL
                   AND (ce.id IS NULL OR ce.source = com.primevest.domain.dividend.CashEventSource.B3_HISTORICO)))
        """)
    BigDecimal sumTotalDividendsByPortfolioId(@Param("portfolioId") UUID portfolioId, @Param("today") LocalDate today);

    /**
     * Total de proventos por posicao de uma carteira, numa consulta so.
     *
     * <p>Substitui portfolio_assets.total_dividends: o total e sempre a soma dos
     * proventos da posicao, e grava-lo era uma segunda fonte que so ficava certa
     * enquanto todo caminho que cria ou apaga provento lembrasse de atualiza-la.
     * Posicao sem provento nao aparece no resultado.</p>
     *
     * <p>Soma o LIQUIDO (V23, docs/planos/PLANO_IR_PROVENTOS.md, D1): e o dinheiro que
     * entrou. Tipo sem regra de IR entra pelo bruto, para nao sumir do retorno.</p>
     *
     * <p>So o que ja foi RECEBIDO ate {@code today}: provento a receber nao e
     * dinheiro na conta e nao entra na Carteira, no Dashboard nem na
     * rentabilidade. Mesma regra de {@link Dividend#isReceived}.</p>
     */
    @Query("""
        SELECT new com.primevest.domain.dividend.PositionDividendTotal(pa.id, SUM(COALESCE(d.netAmount, d.totalAmount)))
        FROM Dividend d
        JOIN d.portfolioAsset pa
        LEFT JOIN d.cashEvent ce
        WHERE pa.portfolio.id = :portfolioId
          AND (d.paymentDate <= :today
               OR (d.paymentDate IS NULL
                   AND (ce.id IS NULL OR ce.source = com.primevest.domain.dividend.CashEventSource.B3_HISTORICO)))
        GROUP BY pa.id
        """)
    List<PositionDividendTotal> sumTotalAmountByPosition(@Param("portfolioId") UUID portfolioId,
                                                         @Param("today") LocalDate today);

    /** Proventos da posicao de certos tipos (devolucoes de capital, no calculo da posicao). */
    List<Dividend> findByPortfolioAssetIdAndTypeIn(UUID portfolioAssetId, Collection<DividendType> types);

    /** Proventos pagos na data, com a posicao, o ativo e o dono (mensagem de D0, docs/planos/PLANO_NOTIFICACOES.md). */
    @Query("""
        SELECT d FROM Dividend d
        JOIN FETCH d.portfolioAsset pa
        JOIN FETCH pa.asset a
        JOIN FETCH pa.portfolio p
        JOIN FETCH p.user
        WHERE d.paymentDate = :date
        ORDER BY a.ticker, d.type, d.exDate
        """)
    List<Dividend> findPaidOnWithOwner(@Param("date") java.time.LocalDate date);

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

    /** Posicoes com provento AUTO cujo IR ainda nao foi calculado (DividendTaxVerifier). */
    @Query("""
        SELECT DISTINCT d.portfolioAsset.id FROM Dividend d
        WHERE d.taxCheckedAt IS NULL
          AND d.origin = com.primevest.domain.dividend.DividendOrigin.AUTO
        """)
    List<UUID> findPositionIdsWithTaxPending();
}
