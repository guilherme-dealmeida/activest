package com.activest.domain.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tipo de tarefa (D24): a rotina — o código que faz o trabalho, achado por {@link #getType()} — e os atributos que ela
 * aceita, com o valor padrão. Muda só por migration (D36).
 */
@Entity
@Table(name = "task_types")
@Getter
@Setter
@NoArgsConstructor
public class TaskType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, updatable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskApi service;

    @Column(nullable = false)
    private boolean enabled = true;

    /** O handler confere o Stop (D8). Gravado na subida pelo serviço dono ({@link TaskStartup}). */
    @Column(nullable = false)
    private boolean stoppable;

    @OneToMany(mappedBy = "taskType", fetch = FetchType.LAZY)
    @OrderBy("position, id")
    private List<TaskTypeAttribute> attributes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = TaskTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TaskTime.now();
    }
}
