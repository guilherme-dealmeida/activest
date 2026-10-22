package com.primevest.domain.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskTypeAttributeRepository extends JpaRepository<TaskTypeAttribute, Long> {

    List<TaskTypeAttribute> findByTaskTypeIdOrderByIdAsc(Long taskTypeId);
}
