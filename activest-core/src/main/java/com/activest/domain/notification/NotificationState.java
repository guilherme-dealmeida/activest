package com.activest.domain.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Estado de uma mensagem para uma pessoa (R1–R5, R9): lida, arquivada, excluída (lógica). Sem linha = não lida, na
 * Entrada.
 */
@Entity
@Table(name = "notification_states")
@IdClass(NotificationState.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class NotificationState {

    @Id
    @Column(name = "notification_id")
    private UUID notificationId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public NotificationState(UUID notificationId, UUID userId) {
        this.notificationId = notificationId;
        this.userId = userId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID notificationId;
        private UUID userId;

        public Key(UUID notificationId, UUID userId) {
            this.notificationId = notificationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(notificationId, k.notificationId)
                && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificationId, userId);
        }
    }
}
