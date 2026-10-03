package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.support.BinanceFake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fila, worker, rotina diaria e aviso de divergencia (PLANO_CRIPTO_BINANCE.md,
 * F5) — com a conta ficticia do {@link BinanceFake}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class IntegracoesFilaIT extends FuncionalBase {

    private static final String BASE = "/api/v1/integrations/connections";

    private User ana;
    private Object conexao;

    @BeforeEach
    void cenario() {
        ana = usuarioComCarteira("ana@primevest.test");
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("provider", "BINANCE");
        corpo.put("label", "Binance");
        corpo.put("apiKey", "chave-ficticia-de-teste-AB12");
        corpo.put("apiSecret", "segredo-ficticio-de-teste");
        corpo.put("consentAccepted", true);
        conexao = post(ana, BASE, corpo, Map.class).getBody().get("id");
    }

    private Map<String, Object> run(Object runId) {
        return get(ana, BASE + "/" + conexao + "/runs/" + runId, Map.class).getBody();
    }

    private String statusDaUltima() {
        return jdbc.queryForObject("SELECT status FROM integration_runs ORDER BY created_at DESC LIMIT 1", String.class);
    }

    @Test
    @DisplayName("Pedir a sincronizacao responde na hora (202, QUEUED) sem ir a corretora; pedir de novo devolve a mesma")
    void pedidoEntraNaFila() {
        ResponseEntity<Map> r = post(ana, BASE + "/" + conexao + "/preview", null, Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(r.getBody().get("status")).isEqualTo("QUEUED");
        assertThat((List<?>) r.getBody().get("items")).isEmpty();

        Object segunda = post(ana, BASE + "/" + conexao + "/preview", null, Map.class).getBody().get("runId");
        assertThat(segunda).isEqualTo(r.getBody().get("runId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_runs", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("O worker processa a fila e para quando ela esvazia")
    void worker() {
        Object runId = post(ana, BASE + "/" + conexao + "/preview", null, Map.class).getBody().get("runId");

        assertThat(integrationService.processNext()).isTrue();
        assertThat(integrationService.processNext()).isFalse();
        assertThat(run(runId).get("status")).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(numero(run(runId).get("newCount"))).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("Chave recusada na busca: execucao FAILED com o motivo, conexao INVALID_CREDENTIALS")
    void falhaNaBusca() {
        BinanceFake.chaveRecusada(binanceClient);

        Map<String, Object> r = sincronizar(ana, conexao);

        assertThat(r.get("status")).isEqualTo("FAILED");
        assertThat((String) r.get("error")).contains("recusou");
        assertThat(jdbc.queryForObject("SELECT status FROM integration_connections", String.class))
            .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("Rotina diaria: a primeira importacao espera o usuario; depois dela, confirma sozinha")
    void rotinaDiaria() {
        assertThat(integrationService.enqueueScheduled()).isEqualTo(1);
        integrationService.processNext();
        assertThat(statusDaUltima()).isEqualTo("AWAITING_CONFIRMATION");

        // Com uma pre-visualizacao aguardando, a rotina nao enfileira outra.
        assertThat(integrationService.enqueueScheduled()).isZero();

        Object runId = jdbc.queryForObject("SELECT id FROM integration_runs ORDER BY created_at DESC LIMIT 1", Object.class);
        post(ana, BASE + "/" + conexao + "/runs/" + runId + "/confirm", null, Map.class);

        assertThat(integrationService.enqueueScheduled()).isEqualTo(1);
        integrationService.processNext();
        assertThat(statusDaUltima()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT trigger_type FROM integration_runs ORDER BY created_at DESC LIMIT 1",
            String.class)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("Rotina diaria nao confirma sozinha quando ha possivel duplicata")
    void rotinaDiariaComDuplicata() {
        Map<String, Object> primeira = sincronizar(ana, conexao);
        post(ana, BASE + "/" + conexao + "/runs/" + primeira.get("runId") + "/cancel", null, String.class);
        // Um "confirmado" anterior, para a rotina poder confirmar sozinha.
        jdbc.update("UPDATE integration_runs SET status = 'SUCCEEDED'");
        lancarComSucesso(ana, compra("BTC", "2024-02-02", "0.002", "400050"));

        integrationService.enqueueScheduled();
        integrationService.processNext();

        assertThat(statusDaUltima()).isEqualTo("AWAITING_CONFIRMATION");
    }

    @Test
    @DisplayName("E3: depois de confirmar, aponta os ativos em que a carteira difere do saldo da corretora (BRL fora)")
    void divergencia() {
        Map<String, Object> p = sincronizar(ana, conexao);
        Map<String, Object> r = post(ana, BASE + "/" + conexao + "/runs/" + p.get("runId") + "/confirm", null, Map.class)
            .getBody();

        // A conta ficticia converteu 0,0001 BTC em 0,003 ETH (cripto x cripto, nao importado):
        // exatamente o que sobra de diferenca.
        List<Map<String, Object>> div = (List<Map<String, Object>>) r.get("divergences");
        assertThat(div).extracting(d -> d.get("asset")).containsExactly("BTC", "ETH");
        Map<String, Object> btc = div.get(0);
        assertThat(numero(btc.get("primevest"))).isEqualByComparingTo("0.001798");
        assertThat(numero(btc.get("exchange"))).isEqualByComparingTo("0.001698");
        assertThat(numero(btc.get("difference"))).isEqualByComparingTo("-0.0001");
        assertThat(numero(div.get(1).get("difference"))).isEqualByComparingTo("0.003");
    }

    @Test
    @DisplayName("F6: historico de execucoes, divergencias na lista de conexoes e origem na transacao")
    void dadosDaTela() {
        Map<String, Object> p = sincronizar(ana, conexao);
        post(ana, BASE + "/" + conexao + "/runs/" + p.get("runId") + "/confirm", null, Map.class);

        List<Map<String, Object>> historico = get(ana, BASE + "/" + conexao + "/runs", List.class).getBody();
        assertThat(historico).singleElement().satisfies(h -> {
            assertThat(h.get("status")).isEqualTo("SUCCEEDED");
            assertThat(numero(h.get("importedCount"))).isEqualByComparingTo("3");
        });

        Map<String, Object> c = (Map<String, Object>) get(ana, BASE, List.class).getBody().get(0);
        assertThat(numero(c.get("divergenceCount"))).isEqualByComparingTo("2");

        assertThat(transacoesDe(ana)).allSatisfy(t -> {
            assertThat(t.connectionLabel()).isEqualTo("Binance");
            assertThat(t.connectionProvider()).isEqualTo("BINANCE");
        });
        Map<String, Object> run = run(p.get("runId"));
        assertThat(run.get("connectionLabel")).isEqualTo("Binance");
        assertThat(run.get("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("Execucao RUNNING perdida (API caiu no meio) vira FAILED e libera a conexao")
    void execucaoPerdida() {
        post(ana, BASE + "/" + conexao + "/preview", null, Map.class);
        jdbc.update("UPDATE integration_runs SET status = 'RUNNING', started_at = NOW() - INTERVAL '2 hours'");

        integrationService.processNext();

        assertThat(statusDaUltima()).isEqualTo("FAILED");
        assertThat(sincronizar(ana, conexao).get("status")).isEqualTo("AWAITING_CONFIRMATION");
    }

    @Test
    @DisplayName("A lista de conexoes mostra a ultima execucao")
    void ultimaExecucaoNaLista() {
        Object runId = post(ana, BASE + "/" + conexao + "/preview", null, Map.class).getBody().get("runId");

        Map<String, Object> c = (Map<String, Object>) get(ana, BASE, List.class).getBody().get(0);
        assertThat(c.get("lastRunId")).isEqualTo(runId);
        assertThat(c.get("lastRunStatus")).isEqualTo("QUEUED");
    }
}
