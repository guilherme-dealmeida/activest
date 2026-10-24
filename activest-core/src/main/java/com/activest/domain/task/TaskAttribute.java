package com.activest.domain.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Valor de um atributo numa tarefa. Nome, tipo do valor, obrigatório e descrição vêm do atributo do tipo (O7). */
@Entity
@Table(name = "task_attributes")
@Getter
@Setter
@NoArgsConstructor
public class TaskAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "task_type_attribute_id", nullable = false, updatable = false)
    private TaskTypeAttribute typeAttribute;

    @Column(name = "att_value", columnDefinition = "text")
    private String attValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TaskAttribute(Task task, TaskTypeAttribute typeAttribute, String attValue) {
        this.task = task;
        this.typeAttribute = typeAttribute;
        this.attValue = attValue;
    }

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
