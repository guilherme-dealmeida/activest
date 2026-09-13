package com.primevest.domain.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Goal> findByUserIdAndStatus(UUID userId, GoalStatus status);
}
