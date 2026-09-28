package com.primevest.domain.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    List<IntegrationConnection> findByStatus(ConnectionStatus status);

    @Query("SELECT c FROM IntegrationConnection c WHERE c.id = :id AND c.user.id = :userId")
    Optional<IntegrationConnection> findOwnedById(@Param("id") UUID id, @Param("userId") UUID userId);
}
