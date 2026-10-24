package com.activest.domain.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskTypeRepository extends JpaRepository<TaskType, Long> {

    Optional<TaskType> findByType(String type);

    List<TaskType> findAllByOrderByTypeAsc();

    List<TaskType> findByServiceOrderByTypeAsc(TaskApi service);
}
