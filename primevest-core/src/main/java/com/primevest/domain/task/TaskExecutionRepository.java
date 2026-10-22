package com.primevest.domain.task;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    boolean existsByTaskId(Long taskId);

    /** A tarefa já teve rodada (qualquer origem e status) desde {@code since} — a subida roda no máximo uma vez por dia. */
    boolean existsByTaskIdAndStartedAtGreaterThanEqual(Long taskId, java.time.LocalDateTime since);

    /** Rodadas em execução dos tipos de um serviço (órfãs na subida). */
    @Query("SELECT e FROM TaskExecution e JOIN FETCH e.task t JOIN FETCH t.taskType y "
         + "WHERE e.status = com.primevest.domain.task.TaskExecutionStatus.RUNNING AND y.service = :service")
    List<TaskExecution> findRunningByService(TaskApi service);

    /** Rodada em execução de um tipo (qualquer tarefa dele), a mais recente. */
    @Query("SELECT e FROM TaskExecution e JOIN FETCH e.task t JOIN FETCH t.taskType y "
         + "WHERE e.status = com.primevest.domain.task.TaskExecutionStatus.RUNNING AND y.type = :type "
         + "ORDER BY e.startedAt DESC")
    List<TaskExecution> findRunningByType(String type);

    /** Acompanhamento: rodadas iniciadas no período, mais nova primeiro. */
    @Query("SELECT e FROM TaskExecution e JOIN FETCH e.task t JOIN FETCH t.taskType y "
         + "WHERE e.startedAt >= :from AND e.startedAt < :to ORDER BY e.startedAt DESC, e.id DESC")
    List<TaskExecution> findStartedBetween(LocalDateTime from, LocalDateTime to);

    /** A última rodada de cada tarefa (coluna "última rodada" da Configuração). */
    @Query("SELECT e FROM TaskExecution e JOIN FETCH e.task t WHERE e.id IN "
         + "(SELECT MAX(x.id) FROM TaskExecution x GROUP BY x.task.id)")
    List<TaskExecution> findLastOfEachTask();

    @Query("SELECT e FROM TaskExecution e WHERE e.task.id = :taskId ORDER BY e.startedAt DESC, e.id DESC")
    List<TaskExecution> findByTask(Long taskId, Pageable page);

    @Query("SELECT e.stopRequestedAt FROM TaskExecution e WHERE e.id = :id")
    LocalDateTime findStopRequestedAt(Long id);

    @Modifying
    @Query("DELETE FROM TaskExecution e WHERE e.startedAt < :before "
         + "AND e.status <> com.primevest.domain.task.TaskExecutionStatus.RUNNING")
    int deleteFinishedBefore(LocalDateTime before);
}
