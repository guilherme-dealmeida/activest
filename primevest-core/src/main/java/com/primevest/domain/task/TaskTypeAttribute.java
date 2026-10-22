package com.primevest.domain.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Atributo que um tipo aceita: nome, tipo do valor, obrigatório, descrição e o valor padrão (nulo = sem padrão). */
@Entity
@Table(name = "task_type_attributes")
@Getter
@Setter
@NoArgsConstructor
public class TaskTypeAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_type_id", nullable = false)
    private TaskType taskType;

    @Column(name = "att_name", nullable = false, length = 80)
    private String attName;

    @Column(name = "att_value", columnDefinition = "text")
    private String attValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 10)
    private TaskValueType valueType;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /** Ordem na tela (V64). */
    @Column(nullable = false)
    private short position;

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
