package com.primevest.functional;

import com.primevest.domain.transaction.dto.TransactionResponseDTO;
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
 * Confirmacao da importacao, duplicatas e as regras E1, E2 e E4
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, F4 e 6.10) — pela API, com a conta ficticia do
 * {@link BinanceFake}: ordem 5001 (compra de 0,001998 BTC liquido), ordem 5002
 * (venda de 0,0002 BTC) e "Comprar Cripto" P1 (0,00025 ETH).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class IntegracoesConfirmacaoIT extends FuncionalBase {

    private static final String BASE = "/api/v1/integrations/connections";
    private static final String ORDEM_COMPRA = "binance:spot:BTCBRL:5001";

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

    private Map<String, Object> preview() {
        Map<String, Object> run = sincronizar(ana, conexao);
        assertThat(run.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        return run;
    }

    private ResponseEntity<Map> confirmar(Object runId, Object corpo) {
        return post(ana, BASE + "/" + conexao + "/runs/" + runId + "/confirm", corpo, Map.class);
    }

    private List<TransactionResponseDTO> importadas() {
        return transacoesDe(ana).stream().filter(t -> t.origin() != null && "AUTO".equals(t.origin().name())).toList();
    }

    private TransactionResponseDTO importada(String ticker, String tipo) {
        return importadas().stream().filter(t -> t.ticker().equals(ticker) && t.type().name().equals(tipo))
            .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Confirmar grava as 3 operacoes como AUTO, pelas regras normais, e a carteira fecha com a corretora")
    void confirmar() {
        Map<String, Object> p = preview();
        assertThat(p.get("status")).isEqualTo("AWAITING_CONFIRMATION");

        ResponseEntity<Map> r = confirmar(p.get("runId"), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("status")).isEqualTo("SUCCEEDED");
        assertThat(importadas()).hasSize(3);
        assertThat(numero(posicaoDe(ana, "BTC").get("totalShares"))).isEqualByComparingTo("0.001798");
        assertThat(numero(posicaoDe(ana, "ETH").get("totalShares"))).isEqualByComparingTo("0.00025");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_cursors", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject(
            "SELECT source_snapshot->>'externalId' FROM transactions WHERE external_id = ?", String.class, ORDEM_COMPRA))
            .isEqualTo(ORDEM_COMPRA);
    }

    @Test
    @DisplayName("Depois de confirmar, a proxima busca e incremental e o que voltar aparece como ja importado")
    void segundaBusca() {
        confirmar(preview().get("runId"), null);

        Map<String, Object> segunda = preview();

        assertThat(numero(segunda.get("newCount"))).isEqualByComparingTo("0");
        // O fake de "Comprar Cripto" ignora o periodo e devolve P1 de novo: ja importado.
        assertThat(numero(segunda.get("existingCount"))).isEqualByComparingTo("1");
        assertThat(importadas()).hasSize(3);
    }

    @Test
    @DisplayName("Confirmar duas vezes a mesma pre-visualizacao: a segunda e recusada")
    void confirmarDuasVezes() {
        Object runId = preview().get("runId");
        confirmar(runId, null);

        assertThat(confirmar(runId, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(importadas()).hasSize(3);
    }

    @Test
    @DisplayName("Nova pre-visualizacao cancela a anterior, que nao pode mais ser confirmada")
    void novaCancelaAnterior() {
        Object primeira = preview().get("runId");
        preview();

        assertThat(confirmar(primeira, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(importadas()).isEmpty();
    }

    @Test
    @DisplayName("6.6: compra digitada antes vira possivel duplicata; sem decisao nao confirma; REPLACE troca o manual pelo importado")
    void duplicataSubstituida() {
        lancarComSucesso(ana, compra("BTC", "2024-02-02", "0.002", "400050"));

        Map<String, Object> p = preview();
        assertThat(numero(p.get("duplicateCount"))).isEqualByComparingTo("1");

        ResponseEntity<Map> semDecisao = confirmar(p.get("runId"), null);
        assertThat(semDecisao.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(importadas()).isEmpty();

        ResponseEntity<Map> r = confirmar(p.get("runId"), Map.of("duplicates", Map.of(ORDEM_COMPRA, "REPLACE")));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transacoesDe(ana)).allSatisfy(t -> assertThat(t.origin().name()).isEqualTo("AUTO"));
        assertThat(numero(posicaoDe(ana, "BTC").get("totalShares"))).isEqualByComparingTo("0.001798");
    }

    @Test
    @DisplayName("6.6: SKIP nao importa a operacao e o lancamento manual fica")
    void duplicataMantidaOuIgnorada() {
        lancarComSucesso(ana, compra("BTC", "2024-02-02", "0.002", "400050"));
        Object runId = preview().get("runId");

        confirmar(runId, Map.of("duplicates", Map.of(ORDEM_COMPRA, "SKIP")));

        assertThat(importadas()).extracting(TransactionResponseDTO::ticker).containsExactlyInAnyOrder("BTC", "ETH");
        assertThat(transacoesDe(ana)).hasSize(3); // manual + venda BTC + ETH
    }

    @Test
    @DisplayName("E1 e E4: editar a importada marca editedAt; restaurar volta aos valores da corretora")
    void editarERestaurar() {
        confirmar(preview().get("runId"), null);
        TransactionResponseDTO compra = importada("BTC", "BUY");

        Map<String, Object> edicao = compra("BTC", "2024-02-02", "0.003", "400050");
        ResponseEntity<TransactionResponseDTO> editada =
            put(ana, "/api/v1/transactions/" + compra.id(), edicao, TransactionResponseDTO.class);
        assertThat(editada.getBody().editedAt()).isNotNull();
        assertThat(editada.getBody().origin().name()).isEqualTo("AUTO");

        ResponseEntity<TransactionResponseDTO> restaurada =
            post(ana, "/api/v1/transactions/" + compra.id() + "/restore", null, TransactionResponseDTO.class);
        assertThat(restaurada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restaurada.getBody().editedAt()).isNull();
        assertThat(restaurada.getBody().shares()).isEqualByComparingTo("0.001998");
        assertThat(restaurada.getBody().brokerageFee()).isEqualByComparingTo("0.80");
    }

    @Test
    @DisplayName("E2 e E4: excluir a importada registra a exclusao; restaurar a traz de volta na hora")
    void excluirERestaurar() {
        confirmar(preview().get("runId"), null);
        TransactionResponseDTO eth = importada("ETH", "BUY");

        assertThat(delete(ana, "/api/v1/transactions/" + eth.id()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<Map> exclusoes = List.of(get(ana, BASE + "/" + conexao + "/exclusions", Map[].class).getBody());
        assertThat(exclusoes).singleElement().satisfies(e -> {
            assertThat(e.get("externalId")).isEqualTo("binance:fiatpay:P1");
            assertThat(e.get("ticker")).isEqualTo("ETH");
        });

        // A proxima busca devolve P1 (o fake ignora o periodo): aparece como excluida, nao volta sozinha.
        assertThat(numero(preview().get("excludedCount"))).isEqualByComparingTo("1");

        ResponseEntity<TransactionResponseDTO> r = post(ana,
            BASE + "/" + conexao + "/exclusions/" + exclusoes.get(0).get("id") + "/restore", null,
            TransactionResponseDTO.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(importada("ETH", "BUY").shares()).isEqualByComparingTo("0.00025");
        assertThat(get(ana, BASE + "/" + conexao + "/exclusions", Map[].class).getBody()).isEmpty();
    }

    @Test
    @DisplayName("Excluir um lancamento MANUAL nao registra exclusao")
    void excluirManual() {
        TransactionResponseDTO manual = lancarComSucesso(ana, compra("BTC", "2024-01-10", "0.001", "400000"));

        delete(ana, "/api/v1/transactions/" + manual.id());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_crypto_exclusions", Integer.class)).isZero();
    }
}
