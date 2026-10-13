package com.primevest.functional;

import com.primevest.domain.notification.NotificationDraft;
import com.primevest.domain.notification.NotificationKind;
import com.primevest.domain.notification.NotificationService;
import com.primevest.domain.user.User;
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
class NotificacoesIT extends FuncionalBase {

    @Autowired
    private NotificationService notifications;

    private UUID avisar(NotificationKind kind, User user, String chave) {
        return notifications.notify(NotificationDraft.of(kind).user(user == null ? null : user.getId())
                .title("Título " + chave).body("Corpo").key(chave).build())
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> caixa(User user, String query) {
        Map<String, Object> page = get(user, "/api/v1/notifications" + query, Map.class).getBody();
        return (List<Map<String, Object>>) page.get("items");
    }

    private Map<?, ?> resumo(User user) {
        return get(user, "/api/v1/notifications/summary", Map.class).getBody();
    }

    private ResponseEntity<Map> patch(User user, String path, Object corpo) {
        HttpHeaders h = headers(user);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(corpo, h), Map.class);
    }

    private User admin(String email) {
        User u = usuarioComCarteira(email);
        jdbc.update("UPDATE users SET is_admin = TRUE WHERE id = ?", u.getId());
        return u;
    }

    @Test
    @DisplayName("Informativa com a mesma chave nunca se repete; pendência só volta depois de resolvida")
    void chaveDeRepeticao() {
        User u = usuarioComCarteira("chave@teste.com");

        assertThat(avisar(NotificationKind.PROVENTO_PAGO, u, "PETR4:2026-10-13")).isNotNull();
        assertThat(avisar(NotificationKind.PROVENTO_PAGO, u, "PETR4:2026-10-13")).isNull();

        UUID pendencia = avisar(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt");
        assertThat(pendencia).isNotNull();
        assertThat(avisar(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt")).isNull();

        // dispensada também segura a chave: a rotina não reabre o que a pessoa dispensou
        patch(u, "/api/v1/notifications/" + pendencia + "/status", Map.of("action", "DISMISS"));
        assertThat(avisar(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt")).isNull();

        assertThat(notifications.resolve(NotificationKind.BONIFICACAO_SEM_CUSTO, u.getId(), "pos:evt")).isEqualTo(1);
        assertThat(avisar(NotificationKind.BONIFICACAO_SEM_CUSTO, u, "pos:evt")).isNotNull();
        assertThat(caixa(u, "")).hasSize(3);
    }

    @Test
    @DisplayName("Estado e status são independentes: resolver não lê, abrir não resolve, excluir não resolve")
    void estadoEStatusIndependentes() {
        User u = usuarioComCarteira("eixos@teste.com");
        UUID id = avisar(NotificationKind.INTEGRACAO_AGUARDANDO, u, "run-1");

        assertThat(resumo(u).get("unread")).isEqualTo(1);
        notifications.resolve(NotificationKind.INTEGRACAO_AGUARDANDO, u.getId(), "run-1");
        Map<String, Object> item = caixa(u, "").get(0);
        assertThat(item.get("status")).isEqualTo("RESOLVIDA");
        assertThat(item.get("read")).isEqualTo(false);

        // ver o detalhe não marca lida (R2); abrir marca
        assertThat(get(u, "/api/v1/notifications/" + id, Map.class).getBody().get("read")).isEqualTo(false);
        assertThat(post(u, "/api/v1/notifications/" + id + "/open", null, Map.class).getBody().get("read"))
            .isEqualTo(true);
        assertThat(resumo(u).get("unread")).isEqualTo(0);

        // marcar como não lida (R5)
        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(id), "action", "UNREAD"));
        assertThat(caixa(u, "?unread=true")).hasSize(1);

        // pendência excluída continua não resolvida no banco e some da caixa (R3)
        UUID outra = avisar(NotificationKind.INTEGRACAO_DIVERGENCIA, u, "conn");
        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(outra), "action", "DELETE"));
        assertThat(caixa(u, "")).extracting(m -> m.get("id")).doesNotContain(outra.toString());
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id = ?", String.class, outra))
            .isEqualTo("NAO_RESOLVIDA");
        assertThat(get(u, "/api/v1/notifications/" + outra, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Arquivar leva para Arquivadas sem mudar lida/não lida; desarquivar volta; contador ignora arquivadas")
    void arquivar() {
        User u = usuarioComCarteira("arquivo@teste.com");
        UUID a = avisar(NotificationKind.RF_LIQUIDADA, u, "tx-1");
        UUID b = avisar(NotificationKind.RF_LIQUIDADA, u, "tx-2");

        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(a, b), "action", "ARCHIVE"));
        assertThat(caixa(u, "")).isEmpty();
        assertThat(caixa(u, "?folder=ARCHIVED")).hasSize(2)
            .allSatisfy(m -> assertThat(m.get("read")).isEqualTo(false));
        assertThat(resumo(u).get("unread")).isEqualTo(0);

        patch(u, "/api/v1/notifications/state", Map.of("ids", List.of(a), "action", "UNARCHIVE"));
        assertThat(caixa(u, "")).hasSize(1);
        assertThat(resumo(u).get("unread")).isEqualTo(1);
    }

    @Test
    @DisplayName("Dispensar e reabrir são da pessoa; informativa não tem status (422)")
    void dispensarEReabrir() {
        User u = usuarioComCarteira("status@teste.com");
        UUID p = avisar(NotificationKind.PRECO_TETO_PADRAO_MUDOU, u, "calc");
        UUID i = avisar(NotificationKind.PRECO_ABAIXO_IDEAL, u, "calc:dia");

        assertThat(patch(u, "/api/v1/notifications/" + p + "/status", Map.of("action", "DISMISS")).getBody()
            .get("status")).isEqualTo("DISPENSADA");
        assertThat(patch(u, "/api/v1/notifications/" + p + "/status", Map.of("action", "REOPEN")).getBody()
            .get("status")).isEqualTo("NAO_RESOLVIDA");
        assertThat(patch(u, "/api/v1/notifications/" + i + "/status", Map.of("action", "DISMISS")).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(caixa(u, "?status=INFORMATIVA")).hasSize(1);
        assertThat(caixa(u, "?status=NAO_RESOLVIDA")).hasSize(1);
    }

    @Test
    @DisplayName("Mensagem de outro usuário não aparece nem abre (404)")
    void outroDono() {
        User dono = usuarioComCarteira("dono@teste.com");
        User outro = usuarioComCarteira("outro@teste.com");
        UUID id = avisar(NotificationKind.RF_LIQUIDADA, dono, "tx");

        assertThat(caixa(outro, "")).isEmpty();
        assertThat(post(outro, "/api/v1/notifications/" + id + "/open", null, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        patch(outro, "/api/v1/notifications/state", Map.of("ids", List.of(id), "action", "DELETE"));
        assertThat(caixa(dono, "")).hasSize(1);
    }

    @Test
    @DisplayName("Administrador: todos os admins veem, estado por pessoa, status para todos; não admin não vê")
    void mensagemDeAdministrador() {
        User a = admin("admin-a@teste.com");
        User b = admin("admin-b@teste.com");
        User comum = usuarioComCarteira("comum@teste.com");
        UUID id = avisar(NotificationKind.CATALOGO_TICKER_NOVO, null, "XPTO3");

        assertThat(resumo(a).get("adminUnread")).isEqualTo(1);
        post(a, "/api/v1/notifications/" + id + "/open", null, Map.class);
        assertThat(resumo(a).get("adminUnread")).isEqualTo(0);
        assertThat(resumo(b).get("adminUnread")).isEqualTo(1);

        patch(b, "/api/v1/notifications/" + id + "/status", Map.of("action", "DISMISS"));
        assertThat(caixa(a, "?audience=ADMIN").get(0).get("status")).isEqualTo("DISPENSADA");

        assertThat(caixa(comum, "")).isEmpty();
        assertThat(caixa(comum, "?audience=ADMIN")).isEmpty();
        assertThat(resumo(comum).get("unread")).isEqualTo(0);
        assertThat(get(comum, "/api/v1/notifications/" + id, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // a conferência seguinte não acha mais o problema: resolve para todos
        assertThat(notifications.resolveAllExcept(NotificationKind.CATALOGO_TICKER_NOVO, null, List.of())).isEqualTo(1);
        assertThat(caixa(b, "?audience=ADMIN").get(0).get("status")).isEqualTo("RESOLVIDA");
    }
}
