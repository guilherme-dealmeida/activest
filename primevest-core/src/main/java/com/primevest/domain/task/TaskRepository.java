package com.primevest.domain.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Tarefas não excluídas, com o tipo. */
    @Query("SELECT t FROM Task t JOIN FETCH t.taskType WHERE t.deletedAt IS NULL ORDER BY t.name")
    List<Task> findAllAlive();

    @Query("SELECT t FROM Task t JOIN FETCH t.taskType WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Task> findAlive(Long id);

    @Query("SELECT t FROM Task t JOIN FETCH t.taskType WHERE t.name = :name AND t.deletedAt IS NULL")
    Optional<Task> findAliveByName(String name);

    /** Tarefas vivas dos tipos de um serviço (o agendador e a recuperação na subida de cada serviço). */
    @Query("SELECT t FROM Task t JOIN FETCH t.taskType y WHERE y.service = :service AND t.deletedAt IS NULL "
         + "ORDER BY t.id")
    List<Task> findAliveByService(TaskApi service);

    @Query("SELECT t FROM Task t JOIN FETCH t.taskType y WHERE y.type = :type AND t.deletedAt IS NULL ORDER BY t.id")
    List<Task> findAliveByType(String type);

    boolean existsByNameAndDeletedAtIsNull(String name);
}
