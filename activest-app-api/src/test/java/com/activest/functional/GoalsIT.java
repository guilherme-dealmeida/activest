package com.activest.functional;

import com.activest.domain.goal.GoalReevaluationQueue;
import com.activest.domain.goal.GoalTracker;
import com.activest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metas pela API (docs/planos/PLANO_METAS.md, Anexo A 15): cadastro pelo catalogo, mesmo numero da Carteira (G7),
 * ciclo de vida, revisoes, conclusao automatica com aviso (D13, D20), fotografia diaria sem duplicar, reavaliacao
 * depois de transacao e autorizacao (404 para meta de outro dono).
 */
class GoalsIT extends FunctionalBase {

    @Autowired private GoalTracker tracker;
    @Autowired private GoalReevaluationQueue queue;

    private User owner;
    private User other;

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("metas@activest.test");
        other = userWithPortfolio("outro-metas@activest.test");
        recordSuccessfully(owner, buy("BBAS3", "2026-01-12", "100", "20.00"));
    }

    private Map<String, Object> patrimony(String target) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Primeiro milhão");
        body.put("type", "PAT_1");
        body.put("targetValue", new BigDecimal(target));
        body.put("scope", Map.of("reservation", "WITH"));
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> create(User u, Map<String, Object> body) {
        ResponseEntity<Map> r = post(u, "/api/v1/goals", body, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> eval(Map<String, Object> goal) {
        return (Map<String, Object>) goal.get("evaluation");
    }

    @Test
    @DisplayName("Catálogo: 17 tipos em 8 categorias, cada um com os campos que o usuário informa")
    @SuppressWarnings("unchecked")
    void catalog() {
        Map<String, Object> c = get(owner, "/api/v1/goals/types", Map.class).getBody();
        assertThat((List<Object>) c.get("types")).hasSize(17);
        assertThat((List<Object>) c.get("categories")).hasSize(8);
    }

    @Test
    @DisplayName("G7: o patrimônio da meta é o valor atual da Carteira; a lista traz o resumo")
    @SuppressWarnings("unchecked")
    void sameNumberAsPortfolio() {
        Map<String, Object> goal = create(owner, patrimony("1000000"));
        BigDecimal portfolio = number(get(owner, "/api/v1/portfolio/", Map.class).getBody().get("currentValue"));
        assertThat(number(eval(goal).get("indicator"))).isEqualByComparingTo(portfolio);
        assertThat(goal.get("lifecycle")).isEqualTo("ATIVA");
        assertThat(goal.get("code")).isEqualTo("PAT-1");

        Map<String, Object> list = get(owner, "/api/v1/goals", Map.class).getBody();
        assertThat((List<Object>) list.get("goals")).hasSize(1);
        assertThat(((Map<String, Object>) list.get("summary")).get("active")).isEqualTo(1);
    }

    @Test
    @DisplayName("Cenário 17: campo calculável no cadastro é 400; tipo sem campo obrigatório é 422; preview não grava")
    void contractAndPreview() {
        Map<String, Object> body = patrimony("1000");
        body.put("params", Map.of("currentValue", 10));
        assertThat(post(owner, "/api/v1/goals", body, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> noReservation = patrimony("1000");
        noReservation.remove("scope");
        assertThat(post(owner, "/api/v1/goals", noReservation, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(post(owner, "/api/v1/goals/preview", patrimony("1000000"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM goals", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Premissas automáticas: vêm sem alvo nem prazo (o formulário já mostra os valores)")
    @SuppressWarnings("unchecked")
    void automaticAssumptions() {
        Map<String, Object> req = new java.util.HashMap<>(Map.of("type", "PAT_1"));
        req.put("params", Map.of("monthlyContribution", 999));
        ResponseEntity<List> r = post(owner, "/api/v1/goals/assumptions", req, List.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> list = r.getBody();
        assertThat(list).extracting(m -> m.get("key")).contains("monthlyContribution", "inflation");
        // o informado e ignorado: o valor e o automatico
        assertThat(list).filteredOn(m -> "monthlyContribution".equals(m.get("key")))
                .allSatisfy(m -> assertThat(m.get("origin")).isEqualTo("AUTOMATICA"));
    }

    @Test
    @DisplayName("Autorização: meta de outro dono responde 404 em todas as rotas")
    void otherOwner() {
        String id = create(owner, patrimony("1000000")).get("id").toString();
        assertThat(get(other, "/api/v1/goals/" + id, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put(other, "/api/v1/goals/" + id, patrimony("5"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(other, "/api/v1/goals/" + id + "/pause", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(other, "/api/v1/goals/" + id + "/history", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(other, "/api/v1/goals/" + id + "/events", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(other, "/api/v1/goals/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Cenário 13: pausar, retomar, concluir, arquivar, desarquivar; transição inválida 422; excluir só a arquivada")
    @SuppressWarnings("unchecked")
    void lifecycle() {
        String id = create(owner, patrimony("1000000")).get("id").toString();
        String base = "/api/v1/goals/" + id;
        assertThat(post(owner, base + "/resume", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(post(owner, base + "/pause", null, Map.class).getBody().get("lifecycle")).isEqualTo("PAUSADA");
        assertThat(post(owner, base + "/resume", null, Map.class).getBody().get("lifecycle")).isEqualTo("ATIVA");
        assertThat(delete(owner, base).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> done = post(owner, base + "/complete", null, Map.class).getBody();
        assertThat(done.get("lifecycle")).isEqualTo("CONCLUIDA");
        assertThat(done.get("evaluation")).isNull();
        assertThat((Map<String, Object>) done.get("finalResult")).containsEntry("by", "USUARIO");
        assertThat(post(owner, base + "/archive", null, Map.class).getBody().get("lifecycle")).isEqualTo("ARQUIVADA");
        assertThat(post(owner, base + "/unarchive", null, Map.class).getBody().get("lifecycle")).isEqualTo("CONCLUIDA");
        post(owner, base + "/archive", null, Map.class);
        assertThat(delete(owner, base).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get(owner, base, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Cenário 15: editar o alvo cria a revisão 2 sem perder o histórico; o tipo não muda")
    @SuppressWarnings("unchecked")
    void revisions() {
        String id = create(owner, patrimony("1000000")).get("id").toString();
        Map<String, Object> edited = put(owner, "/api/v1/goals/" + id, patrimony("2000000"), Map.class).getBody();
        assertThat(edited.get("revision")).isEqualTo(2);
        List<Map<String, Object>> events = List.of(get(owner, "/api/v1/goals/" + id + "/events", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();
        assertThat(events).extracting(e -> e.get("kind")).containsExactly("CRIADA", "EDITADA");
        assertThat(number(((Map<String, Object>) events.get(0).get("definition")).get("targetValue")))
                .isEqualByComparingTo("1000000");
        assertThat(number(((Map<String, Object>) events.get(1).get("definition")).get("targetValue")))
                .isEqualByComparingTo("2000000");
        Map<String, Object> other = patrimony("10");
        other.put("type", "APO_1");
        other.put("scope", Map.of());
        assertThat(put(owner, "/api/v1/goals/" + id, other, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("D13 e D20: a meta que já nasce atingida se conclui sozinha e manda um aviso, uma vez")
    void autoConclusion() {
        Map<String, Object> goal = create(owner, patrimony("100"));
        assertThat(goal.get("lifecycle")).isEqualTo("CONCLUIDA");
        assertThat(goal.get("achievedOn")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND kind = 'META_CONCLUIDA'",
                Integer.class, owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT kind FROM goal_events WHERE goal_id = ?::uuid ORDER BY id", String.class,
                goal.get("id"))).containsExactly("CRIADA", "ATINGIDA", "CONCLUIDA");
        tracker.trackUser(owner.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND kind = 'META_CONCLUIDA'",
                Integer.class, owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Concluída por engano (revisão 26/10/2026): editar corrige o cadastro e reabre; arquivada não edita")
    @SuppressWarnings("unchecked")
    void editReopensCompleted() {
        String id = create(owner, patrimony("100")).get("id").toString();
        Map<String, Object> edited = put(owner, "/api/v1/goals/" + id, patrimony("100000000"), Map.class).getBody();
        assertThat(edited.get("lifecycle")).isEqualTo("ATIVA");
        assertThat(edited.get("revision")).isEqualTo(2);
        assertThat(edited.get("finalResult")).isNull();
        post(owner, "/api/v1/goals/" + id + "/archive", null, Map.class);
        assertThat(put(owner, "/api/v1/goals/" + id, patrimony("200000000"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Cenário 14 e M-A6: transação gravada reavalia a meta (fotografia do dia muda) e a rodada não duplica a fotografia")
    void snapshotAndReevaluation() {
        String id = create(owner, patrimony("1000000")).get("id").toString();
        BigDecimal before = jdbc.queryForObject("SELECT indicator FROM goal_snapshots WHERE goal_id = ?::uuid",
                BigDecimal.class, id);
        recordSuccessfully(owner, buy("BBAS3", "2026-02-12", "100", "20.00"));
        queue.drain();
        BigDecimal after = jdbc.queryForObject("SELECT indicator FROM goal_snapshots WHERE goal_id = ?::uuid",
                BigDecimal.class, id);
        assertThat(after).isGreaterThan(before);
        tracker.trackUser(owner.getId());
        tracker.trackUser(owner.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM goal_snapshots WHERE goal_id = ?::uuid", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Fora do objetivo vira pendência; ao voltar, se resolve sozinha")
    void outsideNotice() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Concentração");
        body.put("type", "RIS_1");
        body.put("targetValue", new BigDecimal("50"));
        body.put("params", Map.of("dimension", "ASSET"));
        String id = create(owner, body).get("id").toString();
        String sql = "SELECT count(*) FROM notifications WHERE user_id = ? AND kind = 'META_FORA_DO_OBJETIVO' AND status = ?";
        assertThat(jdbc.queryForObject(sql, Integer.class, owner.getId(), "NAO_RESOLVIDA")).isEqualTo(1);
        body.put("targetValue", new BigDecimal("100"));
        put(owner, "/api/v1/goals/" + id, body, Map.class);
        assertThat(jdbc.queryForObject(sql, Integer.class, owner.getId(), "NAO_RESOLVIDA")).isZero();
        assertThat(jdbc.queryForObject(sql, Integer.class, owner.getId(), "RESOLVIDA")).isEqualTo(1);
    }
}
