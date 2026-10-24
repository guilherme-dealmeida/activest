package com.activest.domain.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma rodada de uma tarefa (histórico e Acompanhamento). Gravada só pelo executor. */
@Entity
@Table(name = "task_executions")
@Getter
@Setter
@NoArgsConstructor
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TaskExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10, updatable = false)
    private TaskTriggerType triggerType;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    /** Data de vigência (passo 1d). */
    @Column(name = "scheduled_for", nullable = false, updatable = false)
    private LocalDateTime scheduledFor;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "stop_requested_at")
    private LocalDateTime stopRequestedAt;

    @Column(name = "stop_requested_by")
    private UUID stopRequestedBy;

    /** Cópia dos atributos da tarefa no início da rodada ({@code {"FROM_YEAR": "2010"}}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private String parameters;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "log_lines_omitted", nullable = false)
    private int logLinesOmitted;
}
