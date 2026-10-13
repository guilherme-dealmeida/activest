package com.primevest.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas da caixa. "Visível" = do usuário ({@code audience USER}) ou de administrador para quem tem
 * {@code is_admin}, e não excluída por ele (R3).
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    String VISIBLE = """
        ((n.audience = com.primevest.domain.notification.NotificationAudience.USER and n.userId = :me)
          or (n.audience = com.primevest.domain.notification.NotificationAudience.ADMIN and :admin = true))
        and s.deletedAt is null
        """;

    String FILTERS = """
        and ((:archived = false and s.archivedAt is null) or (:archived = true and s.archivedAt is not null))
        and (:audience is null or n.audience = :audience)
        and (:category is null or n.category = :category)
        and (:anyStatus = true or (:informative = true and n.status is null) or n.status = :status)
        and (:unreadOnly = false or s.readAt is null)
        """;

    @Query(value = "select new com.primevest.domain.notification.NotificationView(n, s) from Notification n "
        + "left join NotificationState s on s.notificationId = n.id and s.userId = :me "
        + "where " + VISIBLE + FILTERS + " order by n.createdAt desc, n.id",
        countQuery = "select count(n) from Notification n "
        + "left join NotificationState s on s.notificationId = n.id and s.userId = :me "
        + "where " + VISIBLE + FILTERS)
    Page<NotificationView> findBox(@Param("me") UUID me, @Param("admin") boolean admin,
                                   @Param("archived") boolean archived,
                                   @Param("audience") NotificationAudience audience,
                                   @Param("category") NotificationCategory category,
                                   @Param("anyStatus") boolean anyStatus,
                                   @Param("informative") boolean informative,
                                   @Param("status") NotificationStatus status,
                                   @Param("unreadOnly") boolean unreadOnly,
                                   Pageable pageable);

    @Query("select new com.primevest.domain.notification.NotificationView(n, s) from Notification n "
        + "left join NotificationState s on s.notificationId = n.id and s.userId = :me "
        + "where n.id = :id and " + VISIBLE)
    Optional<NotificationView> findVisible(@Param("id") UUID id, @Param("me") UUID me, @Param("admin") boolean admin);

    /** Não lidas fora de Arquivadas, por público — o contador do sino. */
    @Query("select n.audience, count(n) from Notification n "
        + "left join NotificationState s on s.notificationId = n.id and s.userId = :me "
        + "where " + VISIBLE + " and s.archivedAt is null and s.readAt is null group by n.audience")
    List<Object[]> countUnread(@Param("me") UUID me, @Param("admin") boolean admin);

    /** Ids visíveis entre os pedidos (as ações em lote ignoram o resto). */
    @Query("select n.id from Notification n "
        + "left join NotificationState s on s.notificationId = n.id and s.userId = :me "
        + "where n.id in :ids and " + VISIBLE)
    List<UUID> filterVisible(@Param("ids") Collection<UUID> ids, @Param("me") UUID me, @Param("admin") boolean admin);

    @Query("select n from Notification n where n.kind = :kind "
        + "and ((:userId is null and n.userId is null) or n.userId = :userId) "
        + "and n.status in (com.primevest.domain.notification.NotificationStatus.NAO_RESOLVIDA, "
        + "com.primevest.domain.notification.NotificationStatus.DISPENSADA)")
    List<Notification> findOpen(@Param("kind") NotificationKind kind, @Param("userId") UUID userId);

    @Query("select n from Notification n where n.kind = :kind "
        + "and n.status in (com.primevest.domain.notification.NotificationStatus.NAO_RESOLVIDA, "
        + "com.primevest.domain.notification.NotificationStatus.DISPENSADA)")
    List<Notification> findOpenOfAnyone(@Param("kind") NotificationKind kind);

    @Modifying
    @Query("update Notification n set n.status = com.primevest.domain.notification.NotificationStatus.RESOLVIDA, "
        + "n.statusAt = :now, n.statusBy = null "
        + "where n.id in :ids and n.status in (com.primevest.domain.notification.NotificationStatus.NAO_RESOLVIDA, "
        + "com.primevest.domain.notification.NotificationStatus.DISPENSADA)")
    int resolveIds(@Param("ids") Collection<UUID> ids, @Param("now") LocalDateTime now);

    boolean existsByKindAndUserIdAndDedupKey(NotificationKind kind, UUID userId, String dedupKey);

    List<Notification> findByKindAndUserIdOrderByCreatedAtDesc(NotificationKind kind, UUID userId);
}
