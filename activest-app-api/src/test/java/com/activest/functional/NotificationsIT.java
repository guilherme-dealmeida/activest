package com.activest.functional;

import com.activest.domain.notification.NotificationDraft;
import com.activest.domain.notification.NotificationKind;
import com.activest.domain.notification.NotificationService;
import com.activest.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A caixa de notificações (docs/planos/PLANO_NOTIFICACOES.md, N1/N2): chave de repetição (R10), estado × status
 * independentes (R1–R6), exclusão lógica, mensagens de administrador compartilhadas (R9).
 */
class NotificationsIT extends FunctionalBase {

    @Autowired
    private NotificationService notifications;

    private UUID shouldNotify(NotificationKind kind, User user, String keyValue) {
        return notifications.notify(NotificationDraft.of(kind).user(user == null ? null : user.getId())
                .title("Título " + keyValue).body("Corpo").key(keyValue).build())
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cash(User user, String query) {
        Map<String, Object> page = get(user, "/api/v1/notifications" + query, Map.class).getBody();
        return (List<Map<String, Object>>) page.get("items");
    }

    private Map<?, ?> summary(User user) {
        return get(user, "/api/v1/notifications/summary", Map.class).getBody();
    }

    private ResponseEntity<Map> patch(User user, String path, Object requestBody) {
        HttpHeaders h = headers(user);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(requestBody, h), Map.class);
    }

    private User admin(String email) {
        User u = userWithPortfolio(email);
        jdbc.update("UPDATE users SET is_admin = TRUE WHERE id = ?", u.getId());
        return u;
    }

    @Test
    @DisplayName("Informativa com a mesma chave nunca se repete; pendência só volta depois de resolvida")
    void repeatKey() {
        User u = userWithPortfolio("chave@teste.com");

        assertThat(shouldNotify(NotificationKind.PROVENTO_PAGO, u, "PETR4:2026-10-13")).isNotNull();
        assertThat(shouldNotify(NotificationKind.PROVENTO_PAGO, u, "PETR4:2026-10-13")).isNull();

        UUID pending = shouldNotify(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt");
        assertThat(pending).isNotNull();
        assertThat(shouldNotify(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt")).isNull();

        // dispensada também segura a chave: a rotina não reabre o que a pessoa dispensou
        patch(u, "/api/v1/notifications/" + pending + "/status", Map.of("action", "DISMISS"));
        assertThat(shouldNotify(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt")).isNull();

        assertThat(notifications.resolve(NotificationKind.BONIFICACAO_SEM_CUSTO, u.getId(), "pos:evt")).isEqualTo(1);
        assertThat(shouldNotify(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt")).isNotNull();
        assertThat(cash(u, "")).hasSize(3);
    }

    @Test
    @DisplayName("Estado e status são independentes: resolver não lê, abrir não resolve, excluir não resolve")
    void stateAndStatusIndependent() {
        User u = userWithPortfolio("eixos@teste.com");
        UUID id = shouldNotify(NotificationKind.INTEGRACAO_AGUARDANDO, u, "run-1");

        assertThat(summary(u).get("unread")).isEqualTo(1);
        notifications.resolve(NotificationKind.INTEGRACAO_AGUARDANDO, u.getId(), "run-1");
        Map<String, Object> item = cash(u, "").get(0);
        assertThat(item.get("status")).isEqualTo("RESOLVIDA");
        assertThat(item.get("read")).isEqualTo(false);

        // ver o detalhe não marca lida (R2); abrir marca
        assertThat(get(u, "/api/v1/notifications/" + id, Map.class).getBody().get("read")).isEqualTo(false);
        assertThat(post(u, "/api/v1/notifications/" + id + "/open", null, Map.class).getBody().get("read"))
            .isEqualTo(true);
        assertThat(summary(u).get("unread")).isEqualTo(0);

        // marcar como não lida (R5)
        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(id), "action", "UNREAD"));
        assertThat(cash(u, "?unread=true")).hasSize(1);

        // pendência excluída continua não resolvida no banco e some da caixa (R3)
        UUID other = shouldNotify(NotificationKind.INTEGRACAO_DIVERGENCIA, u, "conn");
        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(other), "action", "DELETE"));
        assertThat(cash(u, "")).extracting(m -> m.get("id")).doesNotContain(other.toString());
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id = ?", String.class, other))
            .isEqualTo("NAO_RESOLVIDA");
        assertThat(get(u, "/api/v1/notifications/" + other, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Arquivar leva para Arquivadas sem mudar lida/não lida; desarquivar volta; contador ignora arquivadas")
    void archive() {
        User u = userWithPortfolio("arquivo@teste.com");
        UUID a = shouldNotify(NotificationKind.RF_LIQUIDADA, u, "tx-1");
        UUID b = shouldNotify(NotificationKind.RF_LIQUIDADA, u, "tx-2");

        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(a, b), "action", "ARCHIVE"));
        assertThat(cash(u, "")).isEmpty();
        assertThat(cash(u, "?folder=ARCHIVED")).hasSize(2)
            .allSatisfy(m -> assertThat(m.get("read")).isEqualTo(false));
        assertThat(summary(u).get("unread")).isEqualTo(0);

        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(a), "action", "UNARCHIVE"));
        assertThat(cash(u, "")).hasSize(1);
        assertThat(summary(u).get("unread")).isEqualTo(1);
    }

    @Test
    @DisplayName("Dispensar e reabrir são da pessoa; informativa não tem status (422)")
    void dismissAndReopen() {
        User u = userWithPortfolio("status@teste.com");
        UUID p = shouldNotify(NotificationKind.PRECO_TETO_PADRAO_MUDOU, u, "calc");
        UUID i = shouldNotify(NotificationKind.PRECO_ABAIXO_IDEAL, u, "calc:dia");

        assertThat(patch(u, "/api/v1/notifications/" + p + "/status", Map.of("action", "DISMISS")).getBody()
            .get("status")).isEqualTo("DISPENSADA");
        assertThat(patch(u, "/api/v1/notifications/" + p + "/status", Map.of("action", "REOPEN")).getBody()
            .get("status")).isEqualTo("NAO_RESOLVIDA");
        assertThat(patch(u, "/api/v1/notifications/" + i + "/status", Map.of("action", "DISMISS")).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(cash(u, "?status=INFORMATIVA")).hasSize(1);
        assertThat(cash(u, "?status=NAO_RESOLVIDA")).hasSize(1);
    }

    @Test
    @DisplayName("Mensagem de outro usuário não aparece nem abre (404)")
    void otherOwner() {
        User owner = userWithPortfolio("dono@teste.com");
        User other = userWithPortfolio("outro@teste.com");
        UUID id = shouldNotify(NotificationKind.RF_LIQUIDADA, owner, "tx");

        assertThat(cash(other, "")).isEmpty();
        assertThat(post(other, "/api/v1/notifications/" + id + "/open", null, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        patch(other, "/api/v1/notifications/state", Map.of("ids", List.of(id), "action", "DELETE"));
        assertThat(cash(owner, "")).hasSize(1);
    }

    @Test
    @DisplayName("Administrador: todos os admins veem, estado por pessoa, status para todos; não admin não vê")
    void adminMessage() {
        User a = admin("admin-a@teste.com");
        User b = admin("admin-b@teste.com");
        User common = userWithPortfolio("comum@teste.com");
        UUID id = shouldNotify(NotificationKind.CATALOGO_TICKER_NOVO, null, "XPTO3");

        assertThat(summary(a).get("adminUnread")).isEqualTo(1);
        post(a, "/api/v1/notifications/" + id + "/open", null, Map.class);
        assertThat(summary(a).get("adminUnread")).isEqualTo(0);
        assertThat(summary(b).get("adminUnread")).isEqualTo(1);

        patch(b, "/api/v1/notifications/" + id + "/status", Map.of("action", "DISMISS"));
        assertThat(cash(a, "?audience=ADMIN").get(0).get("status")).isEqualTo("DISPENSADA");

        assertThat(cash(common, "")).isEmpty();
        assertThat(cash(common, "?audience=ADMIN")).isEmpty();
        assertThat(summary(common).get("unread")).isEqualTo(0);
        assertThat(get(common, "/api/v1/notifications/" + id, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // a conferência seguinte não acha mais o problema: resolve para todos
        assertThat(notifications.resolveAllExcept(NotificationKind.CATALOGO_TICKER_NOVO, null, List.of())).isEqualTo(1);
        assertThat(cash(b, "?audience=ADMIN").get(0).get("status")).isEqualTo("RESOLVIDA");
    }
}
