package com.activest.domain.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tarefa: uma instância de um {@link TaskType}, com nome (editável — D17), horário, ligado/desligado e os seus valores
 * de atributo, espelhados do tipo (O7). Excluída com rodadas = {@code deletedAt} preenchido (O5).
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 120)
    private String cron;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Roda na subida do serviço, no máximo uma vez por dia (D37, D38). */
    @Column(name = "run_on_startup", nullable = false)
    private boolean runOnStartup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_type_id", nullable = false, updatable = false)
    private TaskType taskType;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<TaskAttribute> attributes = new ArrayList<>();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

    /** A descrição desta tarefa ou, vazia, a do tipo (O1). */
    public String getEffectiveDescription() {
        return description == null || description.isBlank() ? taskType.getDescription() : description;
    }

    /** Ligada nela e no tipo, e não excluída. */
    public boolean isActive() {
        return enabled && taskType.isEnabled() && deletedAt == null;
    }

    public Optional<TaskAttribute> attribute(String attName) {
        return attributes.stream().filter(a -> a.getTypeAttribute().getAttName().equals(attName)).findFirst();
    }
}
