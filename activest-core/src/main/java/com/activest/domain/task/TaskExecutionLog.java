package com.activest.domain.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Uma linha do log de uma rodada (D6). Gravada em lote pelo appender do log; aqui só leitura. */
@Entity
@Table(name = "task_execution_logs")
@Getter
@NoArgsConstructor
public class TaskExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private Long executionId;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    @Column(nullable = false, length = 5, updatable = false)
    private String level;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String message;
}
