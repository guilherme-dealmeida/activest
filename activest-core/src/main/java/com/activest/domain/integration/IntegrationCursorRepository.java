package com.activest.domain.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationCursorRepository extends JpaRepository<IntegrationCursor, IntegrationCursorId> {
    List<IntegrationCursor> findByIdConnectionId(UUID connectionId);
}
