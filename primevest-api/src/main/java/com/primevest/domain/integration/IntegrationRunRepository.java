package com.primevest.domain.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationRunRepository extends JpaRepository<IntegrationRun, UUID> {
    List<IntegrationRun> findByConnectionIdOrderByCreatedAtDesc(UUID connectionId);

    List<IntegrationRun> findByConnectionIdAndStatus(UUID connectionId, RunStatus status);

    List<IntegrationRun> findByConnectionIdAndStatusIn(UUID connectionId, java.util.Collection<RunStatus> status);

    List<IntegrationRun> findByStatusAndStartedAtBefore(RunStatus status, java.time.LocalDateTime limite);

    java.util.Optional<IntegrationRun> findFirstByConnectionIdOrderByCreatedAtDesc(UUID connectionId);

    java.util.Optional<IntegrationRun> findFirstByConnectionIdAndStatusOrderByCreatedAtDesc(UUID connectionId, RunStatus status);

    boolean existsByConnectionIdAndStatus(UUID connectionId, RunStatus status);

    /**
     * A fila (F5): a execucao QUEUED mais antiga cuja conexao nao tem outra
     * RUNNING — uma sincronizacao por conexao por vez. SKIP LOCKED deixa dois
     * workers pegarem execucoes diferentes sem se bloquear.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT r.id FROM integration_runs r
        WHERE r.status = 'QUEUED'
          AND NOT EXISTS (SELECT 1 FROM integration_runs x
                          WHERE x.connection_id = r.connection_id AND x.status = 'RUNNING')
        ORDER BY r.created_at
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    java.util.Optional<UUID> lockNextQueued();
}
