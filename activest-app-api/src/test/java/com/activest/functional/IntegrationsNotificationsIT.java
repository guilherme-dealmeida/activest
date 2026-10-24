package com.activest.functional;

import com.activest.domain.user.User;
import com.activest.support.BinanceFake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mensagens das integrações (docs/planos/PLANO_NOTIFICACOES.md, N4): uma por execução; manual só falha e problema de
 * conexão; agendada todas; pendências resolvidas quando o assunto acaba.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class IntegrationsNotificationsIT extends FunctionalBase {

    private static final String BASE = "/api/v1/integrations/connections";

    private User ana;
    private Object connection;

    @BeforeEach
    void scenario() {
        ana = userWithPortfolio("ana-notif@activest.test");
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        Map<String, Object> body = new HashMap<>();
        body.put("provider", "BINANCE");
        body.put("label", "Minha Binance");
        body.put("apiKey", "chave-ficticia-de-teste-AB12");
        body.put("apiSecret", "segredo-ficticio-de-teste");
        body.put("consentAccepted", true);
        connection = post(ana, BASE, body, Map.class).getBody().get("id");
    }

    private List<Map<String, Object>> messages() {
        return jdbc.queryForList("SELECT kind, status, title, payload::text AS payload FROM notifications "
            + "ORDER BY created_at");
    }

    private List<String> types() {
        return messages().stream().map(m -> m.get("kind") + ":" + m.get("status")).toList();
    }

    private Object lastRun() {
        return jdbc.queryForObject("SELECT id FROM integration_runs ORDER BY created_at DESC LIMIT 1", Object.class);
    }

    @Test
    @DisplayName("Chave recusada vira pendência de conexão (não 'falhou'); a busca seguinte que dá certo a resolve")
    void connectionProblem() {
        BinanceFake.rejectedKey(binanceClient);
        sync(ana, connection);
        assertThat(types()).containsExactly("INTEGRACAO_CONEXAO:NAO_RESOLVIDA");

        // de novo com a chave recusada: a mesma pendência, sem repetir
        sync(ana, connection);
        assertThat(types()).hasSize(1);

        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        jdbc.update("UPDATE integration_connections SET status = 'ACTIVE'");
        sync(ana, connection);
        assertThat(types()).containsExactly("INTEGRACAO_CONEXAO:RESOLVIDA");
    }

    @Test
    @DisplayName("Execução manual aguardando e confirmada com divergência: nenhuma mensagem")
    void manualWithoutMessage() {
        Map<String, Object> p = sync(ana, connection);
        post(ana, BASE + "/" + connection + "/runs/" + p.get("runId") + "/confirm", null, Map.class);

        assertThat(messages()).isEmpty();
    }

    @Test
    @DisplayName("Agendada aguardando: pendência; confirmada: resolvida e a divergência vira pendência com os ativos")
    void scheduledAwaitingAndDivergence() {
        integrationService.enqueueScheduled();
        integrationService.processNext();
        assertThat(types()).containsExactly("INTEGRACAO_AGUARDANDO:NAO_RESOLVIDA");

        post(ana, BASE + "/" + connection + "/runs/" + lastRun() + "/confirm", null, Map.class);

        assertThat(types()).containsExactly("INTEGRACAO_AGUARDANDO:RESOLVIDA", "INTEGRACAO_DIVERGENCIA:NAO_RESOLVIDA");
        assertThat((String) messages().get(1).get("payload")).contains("BTC").contains("ETH");
        assertThat((String) messages().get(1).get("title")).isEqualTo("Minha Binance — 2 ativos divergem da Binance");
    }

    @Test
    @DisplayName("Agendada descartada: a pendência fica resolvida (D6)")
    void scheduledDiscarded() {
        integrationService.enqueueScheduled();
        integrationService.processNext();
        post(ana, BASE + "/" + connection + "/runs/" + lastRun() + "/cancel", null, String.class);

        assertThat(types()).containsExactly("INTEGRACAO_AGUARDANDO:RESOLVIDA");
    }

    @Test
    @DisplayName("Agendada que confirma sozinha com operações novas: mensagem informativa com o número")
    void scheduledImported() {
        Map<String, Object> first = sync(ana, connection);
        post(ana, BASE + "/" + connection + "/runs/" + first.get("runId") + "/cancel", null, String.class);
        // Um "confirmado" anterior, para a rotina poder confirmar sozinha.
        jdbc.update("UPDATE integration_runs SET status = 'SUCCEEDED'");

        integrationService.enqueueScheduled();
        integrationService.processNext();

        assertThat(types()).contains("INTEGRACAO_IMPORTOU:null");
        assertThat(messages()).filteredOn(m -> "INTEGRACAO_IMPORTOU".equals(m.get("kind")))
            .extracting(m -> m.get("title")).containsExactly("Minha Binance — 3 operações importadas");
    }

    @Test
    @DisplayName("Desconectar resolve as pendências da conexão")
    void disconnect() {
        BinanceFake.rejectedKey(binanceClient);
        sync(ana, connection);
        delete(ana, BASE + "/" + connection);

        assertThat(types()).containsExactly("INTEGRACAO_CONEXAO:RESOLVIDA");
    }
}
