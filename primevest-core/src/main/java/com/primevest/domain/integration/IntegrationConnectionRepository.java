package com.primevest.domain.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas escopadas pelo dono, no criterio do projeto: conexao de outro
 * usuario responde "nao encontrada", e o filtro vai para dentro da consulta.
 */
@Repository
public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, UUID> {

    @Query("SELECT c FROM IntegrationConnection c WHERE c.user.id = :userId ORDER BY c.createdAt")
    List<IntegrationConnection> findAllOwned(@Param("userId") UUID userId);

    boolean existsByUserIdAndLabelIgnoreCase(UUID userId, String label);

    List<IntegrationConnection> findByStatusIn(Collection<ConnectionStatus> statuses);

    /** Conexoes vivas da fonte, de qualquer usuario: para o CPF unico entre conexoes (B3, armadilha A1). */
    List<IntegrationConnection> findByProviderAndStatusNot(IntegrationProvider provider, ConnectionStatus status);

    /** O usuario tem conexao viva com a fonte? (B3: o CPF do perfil nao muda com conexao viva, D20.) */
    boolean existsByUserIdAndProviderAndStatusNot(UUID userId, IntegrationProvider provider, ConnectionStatus status);

    @Query("SELECT c FROM IntegrationConnection c WHERE c.id = :id AND c.user.id = :userId")
    Optional<IntegrationConnection> findOwnedById(@Param("id") UUID id, @Param("userId") UUID userId);
}
