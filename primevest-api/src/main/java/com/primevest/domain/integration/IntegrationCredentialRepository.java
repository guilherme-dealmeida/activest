package com.primevest.domain.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Chave = id da conexao (1:1). So o CredentialVault usa este repositorio.
 */
@Repository
public interface IntegrationCredentialRepository extends JpaRepository<IntegrationCredential, UUID> {
}
