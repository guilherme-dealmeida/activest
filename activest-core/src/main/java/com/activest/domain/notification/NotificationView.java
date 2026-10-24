package com.activest.domain.notification;

/** Uma mensagem com o estado dela para quem está lendo ({@code state} nulo = não lida, na Entrada). */
public record NotificationView(Notification notification, NotificationState state) {

    public boolean read() {
        return state != null && state.getReadAt() != null;
    }

    public boolean archived() {
        return state != null && state.getArchivedAt() != null;
    }
}
