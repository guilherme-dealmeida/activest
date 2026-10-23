package com.activest.domain.ceilingprice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CeilingPriceRepository extends JpaRepository<CeilingPrice, UUID> {
    List<CeilingPrice> findByUserId(UUID userId);
    Optional<CeilingPrice> findByUserIdAndAssetId(UUID userId, Long assetId);

    /**
     * Preco teto pelo id, mas so se for do usuario. O id vem do cliente, entao
     * o dono entra na propria consulta: linha de outro nao chega a ser lida.
     */
    Optional<CeilingPrice> findByIdAndUserId(UUID id, UUID userId);

    /** Cálculos com o alerta de preço ligado (docs/planos/PLANO_NOTIFICACOES.md, 5.5). */
    List<CeilingPrice> findByAlertEnabledTrue();

    List<CeilingPrice> findByAssetIdIn(java.util.Collection<Long> assetIds);
}
