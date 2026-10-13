package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.support.BinanceFake;
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
class IntegracoesNotificacoesIT extends FuncionalBase {

    private static final String BASE = "/api/v1/integrations/connections";

    private User ana;
    private Object conexao;

    @BeforeEach
    void cenario() {
        ana = usuarioComCarteira("ana-notif@primevest.test");
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("provider", "BINANCE");
        corpo.put("label", "Minha Binance");
        corpo.put("apiKey", "chave-ficticia-de-teste-AB12");
        corpo.put("apiSecret", "segredo-ficticio-de-teste");
        corpo.put("consentAccepted", true);
        conexao = post(ana, BASE, corpo, Map.class).getBody().get("id");
    }

    private List<Map<String, Object>> mensagens() {
        return jdbc.queryForList("SELECT kind, status, title, payload::text AS payload FROM notifications "
            + "ORDER BY created_at");
    }

    private List<String> tipos() {
        return mensagens().stream().map(m -> m.get("kind") + ":" + m.get("status")).toList();
    }

    private Object ultimaExecucao() {
        return jdbc.queryForObject("SELECT id FROM integration_runs ORDER BY created_at DESC LIMIT 1", Object.class);
    }

    @Test
    @DisplayName("Chave recusada vira pendência de conexão (não 'falhou'); a busca seguinte que dá certo a resolve")
    void problemaDeConexao() {
        BinanceFake.chaveRecusada(binanceClient);
        sincronizar(ana, conexao);
        assertThat(tipos()).containsExactly("INTEGRACAO_CONEXAO:NAO_RESOLVIDA");

        // de novo com a chave recusada: a mesma pendência, sem repetir
        sincronizar(ana, conexao);
        assertThat(tipos()).hasSize(1);

        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        jdbc.update("UPDATE integration_connections SET status = 'ACTIVE'");
        sincronizar(ana, conexao);
        assertThat(tipos()).containsExactly("INTEGRACAO_CONEXAO:RESOLVIDA");
    }

    @Test
    @DisplayName("Execução manual aguardando e confirmada com divergência: nenhuma mensagem")
    void manualSemMensagem() {
        Map<String, Object> p = sincronizar(ana, conexao);
        post(ana, BASE + "/" + conexao + "/runs/" + p.get("runId") + "/confirm", null, Map.class);

        assertThat(mensagens()).isEmpty();
    }

    @Test
    @DisplayName("Agendada aguardando: pendência; confirmada: resolvida e a divergência vira pendência com os ativos")
    void agendadaAguardandoEDivergencia() {
        integrationService.enqueueScheduled();
        integrationService.processNext();
        assertThat(tipos()).containsExactly("INTEGRACAO_AGUARDANDO:NAO_RESOLVIDA");

        post(ana, BASE + "/" + conexao + "/runs/" + ultimaExecucao() + "/confirm", null, Map.class);

        assertThat(tipos()).containsExactly("INTEGRACAO_AGUARDANDO:RESOLVIDA", "INTEGRACAO_DIVERGENCIA:NAO_RESOLVIDA");
        assertThat((String) mensagens().get(1).get("payload")).contains("BTC").contains("ETH");
        assertThat((String) mensagens().get(1).get("title")).isEqualTo("Minha Binance — 2 ativos divergem da Binance");
    }

    @Test
    @DisplayName("Agendada descartada: a pendência fica resolvida (D6)")
    void agendadaDescartada() {
        integrationService.enqueueScheduled();
        integrationService.processNext();
        post(ana, BASE + "/" + conexao + "/runs/" + ultimaExecucao() + "/cancel", null, String.class);

        assertThat(tipos()).containsExactly("INTEGRACAO_AGUARDANDO:RESOLVIDA");
    }

    @Test
    @DisplayName("Agendada que confirma sozinha com operações novas: mensagem informativa com o número")
    void agendadaImportou() {
        Map<String, Object> primeira = sincronizar(ana, conexao);
        post(ana, BASE + "/" + conexao + "/runs/" + primeira.get("runId") + "/cancel", null, String.class);
        // Um "confirmado" anterior, para a rotina poder confirmar sozinha.
        jdbc.update("UPDATE integration_runs SET status = 'SUCCEEDED'");

        integrationService.enqueueScheduled();
        integrationService.processNext();

        assertThat(tipos()).contains("INTEGRACAO_IMPORTOU:null");
        assertThat(mensagens()).filteredOn(m -> "INTEGRACAO_IMPORTOU".equals(m.get("kind")))
            .extracting(m -> m.get("title")).containsExactly("Minha Binance — 3 operações importadas");
    }

    @Test
    @DisplayName("Desconectar resolve as pendências da conexão")
    void desconectar() {
        BinanceFake.chaveRecusada(binanceClient);
        sincronizar(ana, conexao);
        delete(ana, BASE + "/" + conexao);

        assertThat(tipos()).containsExactly("INTEGRACAO_CONEXAO:RESOLVIDA");
    }
}
