package com.activest.domain.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface GoalSnapshotRepository extends JpaRepository<GoalSnapshot, GoalSnapshot.Key> {

    List<GoalSnapshot> findByGoalIdAndReferenceDateBetweenOrderByReferenceDateAsc(UUID goalId, LocalDate from,
                                                                                   LocalDate to);

    long countByGoalId(UUID goalId);

    java.util.Optional<GoalSnapshot> findFirstByGoalIdAndReferenceDateLessThanOrderByReferenceDateDesc(UUID goalId,
                                                                                                  LocalDate date);
}
