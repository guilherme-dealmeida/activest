package com.activest.domain.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationRunItemRepository extends JpaRepository<IntegrationRunItem, UUID> {
    List<IntegrationRunItem> findByRunId(UUID runId);

    List<IntegrationRunItem> findByRunIdOrderByCreatedAt(UUID runId);
}
