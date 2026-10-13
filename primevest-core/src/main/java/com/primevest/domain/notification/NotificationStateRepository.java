package com.primevest.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationStateRepository extends JpaRepository<NotificationState, NotificationState.Key> {
    List<NotificationState> findByUserIdAndNotificationIdIn(UUID userId, Collection<UUID> notificationIds);
}
