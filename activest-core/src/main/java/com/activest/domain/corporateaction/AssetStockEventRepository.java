package com.activest.domain.corporateaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetStockEventRepository extends JpaRepository<AssetStockEvent, Long> {

    List<AssetStockEvent> findByAssetIdOrderByDataComAsc(Long assetId);

    /** Bonificacoes sem custo atribuido pela B3 (docs/planos/PLANO_NOTIFICACOES.md, 5.4). */
    @Query("SELECT e FROM AssetStockEvent e JOIN FETCH e.asset WHERE e.type = :type AND e.assignedCost IS NULL "
            + "ORDER BY e.dataCom, e.id")
    List<AssetStockEvent> findWithoutCost(@Param("type") StockEventType type);

    /** Eventos com data-com no intervalo (a data-ex de hoje vem da data-com do pregao anterior). */
    @Query("SELECT e FROM AssetStockEvent e JOIN FETCH e.asset WHERE e.dataCom BETWEEN :from AND :to "
            + "ORDER BY e.dataCom, e.id")
    List<AssetStockEvent> findByDataComBetween(@Param("from") java.time.LocalDate from,
                                               @Param("to") java.time.LocalDate to);

    @Query("SELECT e FROM AssetStockEvent e WHERE e.asset.id IN :assetIds ORDER BY e.dataCom ASC, e.id ASC")
    List<AssetStockEvent> findByAssetIds(@Param("assetIds") Collection<Long> assetIds);

    Optional<AssetStockEvent> findByAssetIdAndTypeAndDataComAndFactor(Long assetId, StockEventType type,
                                                                     LocalDate dataCom, BigDecimal factor);
}
