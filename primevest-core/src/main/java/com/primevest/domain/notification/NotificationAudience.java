package com.primevest.domain.notification;

/** Para quem é a mensagem (docs/planos/PLANO_NOTIFICACOES.md, 3.1). */
public enum NotificationAudience {
    /** Um usuário (notifications.user_id). */
    USER,
    /** Todos os administradores (is_admin): estado por pessoa, status para todos (R9). */
    ADMIN
}
