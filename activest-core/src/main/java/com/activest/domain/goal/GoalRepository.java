package com.activest.domain.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** A meta so se o dono for este usuario (Claude.md, Autorizacao: de outro dono responde "nao encontrada"). */
    @Query("SELECT g FROM Goal g WHERE g.id = :id AND g.user.id = :userId")
    Optional<Goal> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT DISTINCT g.user.id FROM Goal g WHERE g.lifecycle IN :lifecycles")
    List<UUID> usersWith(@Param("lifecycles") Collection<GoalLifecycle> lifecycles);

    List<Goal> findByUserIdAndLifecycleIn(UUID userId, Collection<GoalLifecycle> lifecycles);
}
