package com.activest.domain.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalEventRepository extends JpaRepository<GoalEvent, Long> {

    List<GoalEvent> findByGoalIdOrderByOccurredAtAscIdAsc(UUID goalId);
}
