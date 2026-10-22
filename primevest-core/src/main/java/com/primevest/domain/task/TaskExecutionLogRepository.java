package com.primevest.domain.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;

public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

    Page<TaskExecutionLog> findByExecutionIdOrderByIdAsc(Long executionId, Pageable page);

    Page<TaskExecutionLog> findByExecutionIdAndLevelInOrderByIdAsc(Long executionId, Collection<String> levels,
                                                                  Pageable page);

    long countByExecutionId(Long executionId);

    @Modifying
    @Query("DELETE FROM TaskExecutionLog l WHERE l.loggedAt < :before")
    int deleteLoggedBefore(LocalDateTime before);
}
