package com.primevest.domain.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface IntegrationCryptoExclusionRepository extends JpaRepository<IntegrationCryptoExclusion, UUID> {

    /**
     * Dos ids que a fonte devolveu, quais o usuario excluiu. Uma consulta por
     * sincronizacao, em vez de uma por operacao.
     */
    @Query("SELECT e.externalId FROM IntegrationCryptoExclusion e "
         + "WHERE e.connection.id = :connectionId AND e.externalId IN :externalIds")
    Set<String> findExcludedIds(@Param("connectionId") UUID connectionId,
                                @Param("externalIds") Collection<String> externalIds);

    List<IntegrationCryptoExclusion> findByConnectionIdOrderByExcludedAtDesc(UUID connectionId);
}
