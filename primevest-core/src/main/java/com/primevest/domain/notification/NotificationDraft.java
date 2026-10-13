package com.primevest.domain.notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que um produtor quer avisar. {@code dedupKey} segue a R10: a informativa com a mesma chave nunca se repete; a
 * pendência não se repete enquanto não for resolvida. A chave é por público e dono — o serviço completa com o tipo.
 *
 * <p>{@code lines}: linhas de detalhe (ex.: um provento por linha); {@code actions}: botões da mensagem
 * ({@link Action}).</p>
 */
public record NotificationDraft(NotificationKind kind, UUID userId, String title, String body,
                                String refType, String refId, String dedupKey,
                                List<String> lines, List<Action> actions, Map<String, Object> data) {

    /**
     * Botão da mensagem. {@code type}: LINK (vai para {@code target}) ou uma ação própria que a tela conhece
     * (ex.: INFORMAR_CUSTO). {@code params}: o que a ação precisa.
     */
    public record Action(String type, String label, String target, Map<String, Object> params) {
        public static Action link(String label, String target) {
            return new Action("LINK", label, target, Map.of());
        }
    }

    public static Builder of(NotificationKind kind) {
        return new Builder(kind);
    }

    public static final class Builder {
        private final NotificationKind kind;
        private UUID userId;
        private String title;
        private String body;
        private String refType;
        private String refId;
        private String dedupKey;
        private final List<String> lines = new ArrayList<>();
        private final List<Action> actions = new ArrayList<>();
        private final Map<String, Object> data = new LinkedHashMap<>();

        private Builder(NotificationKind kind) {
            this.kind = kind;
        }

        public Builder user(UUID userId) { this.userId = userId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder body(String body) { this.body = body; return this; }
        public Builder ref(String type, Object id) {
            this.refType = type;
            this.refId = id == null ? null : String.valueOf(id);
            return this;
        }
        public Builder key(String dedupKey) { this.dedupKey = dedupKey; return this; }
        public Builder line(String line) { this.lines.add(line); return this; }
        public Builder lines(java.util.Collection<String> lines) { this.lines.addAll(lines); return this; }
        public Builder action(Action action) { this.actions.add(action); return this; }
        public Builder link(String label, String target) { return action(Action.link(label, target)); }
        public Builder data(String key, Object value) { this.data.put(key, value); return this; }

        public NotificationDraft build() {
            return new NotificationDraft(kind, userId, title, body, refType, refId, dedupKey,
                List.copyOf(lines), List.copyOf(actions), Map.copyOf(data));
        }
    }
}
