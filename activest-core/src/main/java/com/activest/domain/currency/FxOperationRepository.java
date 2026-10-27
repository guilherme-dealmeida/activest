package com.activest.domain.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FxOperationRepository extends JpaRepository<FxOperation, UUID> {

    List<FxOperation> findByUserIdOrderByOperationDateDescCreatedAtDesc(UUID userId);

    Optional<FxOperation> findByIdAndUserId(UUID id, UUID userId);
}
