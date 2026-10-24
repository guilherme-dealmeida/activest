package com.activest.domain.notification;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Contratos da API de notificações (docs/planos/PLANO_NOTIFICACOES.md, 5.2). */
public final class NotificationDTOs {

    private NotificationDTOs() {
    }

    /** Uma mensagem como a pessoa a vê: o estado dela (lida/arquivada) e o status do assunto. */
    public record Item(UUID id, String audience, String kind, String category, String severity, String title,
                       String body, String refType, String refId, JsonNode payload, String status,
                       LocalDateTime statusAt, boolean read, boolean archived, LocalDateTime createdAt) {
    }

    public record Page(List<Item> items, int page, int size, long total) {
    }

    /** O sino: não lidas fora de Arquivadas (as de administrador separadas). */
    public record Summary(long unread, long adminUnread) {
    }

    public record StateRequest(List<UUID> ids, NotificationService.StateAction action) {
    }

    public record StateResult(int changed) {
    }

    public enum StatusAction { DISMISS, REOPEN }

    public record StatusRequest(StatusAction action) {
    }
}
