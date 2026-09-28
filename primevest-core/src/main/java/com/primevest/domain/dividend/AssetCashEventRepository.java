package com.primevest.domain.dividend;

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
public interface AssetCashEventRepository extends JpaRepository<AssetCashEvent, Long> {

    List<AssetCashEvent> findByAssetIdOrderByDataComAsc(Long assetId);

    /**
     * A mesma chave da unicidade do banco (uk_asset_cash_events). O tipo pode
     * ser nulo (rotulo desconhecido), por isso a comparacao com IS NULL.
     */
    @Query("""
        SELECT e FROM AssetCashEvent e
        WHERE e.asset.id = :assetId
          AND ((:type IS NULL AND e.type IS NULL) OR e.type = :type)
          AND e.dataCom = :dataCom
          AND e.valuePerShare = :value
          AND e.seq = :seq
        """)
    Optional<AssetCashEvent> findSame(@Param("assetId") Long assetId, @Param("type") DividendType type,
                                      @Param("dataCom") LocalDate dataCom, @Param("value") BigDecimal value,
                                      @Param("seq") Short seq);

    /**
     * Data-com mais antiga que a B3 recente (~12 meses) trouxe para o ativo.
     * Posicao que comeca antes dela precisa do historico longo. Nula = a B3
     * nunca publicou provento do ativo.
     */
    @Query("""
        SELECT MIN(e.dataCom) FROM AssetCashEvent e
        WHERE e.asset.id = :assetId
          AND e.source = com.primevest.domain.dividend.CashEventSource.B3
        """)
    LocalDate findOldestRecentDataCom(@Param("assetId") Long assetId);

    List<AssetCashEvent> findByAssetIdInAndTypeIsNotNull(Collection<Long> assetIds);

    /** Avisos do FNET ja gravados para o ativo (id do documento): nao sao baixados de novo. */
    @Query("""
        SELECT e.sourceRef FROM AssetCashEvent e
        WHERE e.asset.id = :assetId
          AND e.source = com.primevest.domain.dividend.CashEventSource.FNET
          AND e.sourceRef IS NOT NULL
        """)
    java.util.Set<String> findFnetRefs(@Param("assetId") Long assetId);

    /** O ativo ja tem algum evento desta fonte (historico longo da B3 gravado, F10). */
    boolean existsByAssetIdAndSource(Long assetId, CashEventSource source);
}
