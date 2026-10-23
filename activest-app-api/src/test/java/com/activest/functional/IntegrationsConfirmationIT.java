package com.activest.functional;

import com.activest.domain.transaction.dto.TransactionResponseDTO;
import com.activest.domain.user.User;
import com.activest.support.BinanceFake;
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
class IntegrationsConfirmationIT extends FunctionalBase {

    private static final String BASE = "/api/v1/integrations/connections";
    private static final String BUY_ORDER = "binance:spot:BTCBRL:5001";

    private User ana;
    private Object connection;

    @BeforeEach
    void scenario() {
        ana = userWithPortfolio("ana@activest.test");
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        Map<String, Object> body = new HashMap<>();
        body.put("provider", "BINANCE");
        body.put("label", "Binance");
        body.put("apiKey", "chave-ficticia-de-teste-AB12");
        body.put("apiSecret", "segredo-ficticio-de-teste");
        body.put("consentAccepted", true);
        connection = post(ana, BASE, body, Map.class).getBody().get("id");
    }

    private Map<String, Object> preview() {
        Map<String, Object> run = sync(ana, connection);
        assertThat(run.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        return run;
    }

    private ResponseEntity<Map> doConfirm(Object runId, Object body) {
        return post(ana, BASE + "/" + connection + "/runs/" + runId + "/confirm", body, Map.class);
    }

    private List<TransactionResponseDTO> importedCount() {
        return transactionsOf(ana).stream().filter(t -> t.origin() != null && "AUTO".equals(t.origin().name())).toList();
    }

    private TransactionResponseDTO imported(String ticker, String typeValue) {
        return importedCount().stream().filter(t -> t.ticker().equals(ticker) && t.type().name().equals(typeValue))
            .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Confirmar grava as 3 operacoes como AUTO, pelas regras normais, e a carteira fecha com a corretora")
    void doConfirm() {
        Map<String, Object> p = preview();
        assertThat(p.get("status")).isEqualTo("AWAITING_CONFIRMATION");

        ResponseEntity<Map> r = doConfirm(p.get("runId"), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("status")).isEqualTo("SUCCEEDED");
        assertThat(importedCount()).hasSize(3);
        assertThat(number(positionOf(ana, "BTC").get("totalShares"))).isEqualByComparingTo("0.001798");
        assertThat(number(positionOf(ana, "ETH").get("totalShares"))).isEqualByComparingTo("0.00025");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_cursors", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject(
            "SELECT source_snapshot->>'externalId' FROM transactions WHERE external_id = ?", String.class, BUY_ORDER))
            .isEqualTo(BUY_ORDER);
    }

    @Test
    @DisplayName("Depois de confirmar, a proxima busca e incremental e o que voltar aparece como ja importado")
    void secondFetch() {
        doConfirm(preview().get("runId"), null);

        Map<String, Object> second = preview();

        assertThat(number(second.get("newCount"))).isEqualByComparingTo("0");
        // O fake de "Comprar Cripto" ignora o periodo e devolve P1 de novo: ja importado.
        assertThat(number(second.get("existingCount"))).isEqualByComparingTo("1");
        assertThat(importedCount()).hasSize(3);
    }

    @Test
    @DisplayName("Confirmar duas vezes a mesma pre-visualizacao: a segunda e recusada")
    void confirmTwice() {
        Object runId = preview().get("runId");
        doConfirm(runId, null);

        assertThat(doConfirm(runId, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(importedCount()).hasSize(3);
    }

    @Test
    @DisplayName("Nova pre-visualizacao cancela a anterior, que nao pode mais ser confirmada")
    void newCancelsPrevious() {
        Object first = preview().get("runId");
        preview();

        assertThat(doConfirm(first, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(importedCount()).isEmpty();
    }

    @Test
    @DisplayName("6.6: compra digitada antes vira possivel duplicata; sem decisao nao confirma; REPLACE troca o manual pelo importado")
    void duplicateReplaced() {
        recordSuccessfully(ana, buy("BTC", "2024-02-02", "0.002", "400050"));

        Map<String, Object> p = preview();
        assertThat(number(p.get("duplicateCount"))).isEqualByComparingTo("1");

        ResponseEntity<Map> undecided = doConfirm(p.get("runId"), null);
        assertThat(undecided.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(importedCount()).isEmpty();

        ResponseEntity<Map> r = doConfirm(p.get("runId"), Map.of("duplicates", Map.of(BUY_ORDER, "REPLACE")));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transactionsOf(ana)).allSatisfy(t -> assertThat(t.origin().name()).isEqualTo("AUTO"));
        assertThat(number(positionOf(ana, "BTC").get("totalShares"))).isEqualByComparingTo("0.001798");
    }

    @Test
    @DisplayName("6.6: SKIP nao importa a operacao e o lancamento manual fica")
    void duplicateKeptOrIgnored() {
        recordSuccessfully(ana, buy("BTC", "2024-02-02", "0.002", "400050"));
        Object runId = preview().get("runId");

        doConfirm(runId, Map.of("duplicates", Map.of(BUY_ORDER, "SKIP")));

        assertThat(importedCount()).extracting(TransactionResponseDTO::ticker).containsExactlyInAnyOrder("BTC", "ETH");
        assertThat(transactionsOf(ana)).hasSize(3); // manual + venda BTC + ETH
    }

    @Test
    @DisplayName("E1 e E4: editar a importada marca editedAt; restaurar volta aos valores da corretora")
    void editAndRestore() {
        doConfirm(preview().get("runId"), null);
        TransactionResponseDTO buy = imported("BTC", "BUY");

        Map<String, Object> edit = buy("BTC", "2024-02-02", "0.003", "400050");
        ResponseEntity<TransactionResponseDTO> edited =
            put(ana, "/api/v1/transactions/" + buy.id(), edit, TransactionResponseDTO.class);
        assertThat(edited.getBody().editedAt()).isNotNull();
        assertThat(edited.getBody().origin().name()).isEqualTo("AUTO");

        ResponseEntity<TransactionResponseDTO> restored =
            post(ana, "/api/v1/transactions/" + buy.id() + "/restore", null, TransactionResponseDTO.class);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restored.getBody().editedAt()).isNull();
        assertThat(restored.getBody().shares()).isEqualByComparingTo("0.001998");
        assertThat(restored.getBody().brokerageFee()).isEqualByComparingTo("0.80");
    }

    @Test
    @DisplayName("E2 e E4: excluir a importada registra a exclusao; restaurar a traz de volta na hora")
    void deleteAndRestore() {
        doConfirm(preview().get("runId"), null);
        TransactionResponseDTO eth = imported("ETH", "BUY");

        assertThat(delete(ana, "/api/v1/transactions/" + eth.id()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<Map> exclusions = List.of(get(ana, BASE + "/" + connection + "/exclusions", Map[].class).getBody());
        assertThat(exclusions).singleElement().satisfies(e -> {
            assertThat(e.get("externalId")).isEqualTo("binance:fiatpay:P1");
            assertThat(e.get("ticker")).isEqualTo("ETH");
        });

        // A proxima busca devolve P1 (o fake ignora o periodo): aparece como excluida, nao volta sozinha.
        assertThat(number(preview().get("excludedCount"))).isEqualByComparingTo("1");

        ResponseEntity<TransactionResponseDTO> r = post(ana,
            BASE + "/" + connection + "/exclusions/" + exclusions.get(0).get("id") + "/restore", null,
            TransactionResponseDTO.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(imported("ETH", "BUY").shares()).isEqualByComparingTo("0.00025");
        assertThat(get(ana, BASE + "/" + connection + "/exclusions", Map[].class).getBody()).isEmpty();
    }

    @Test
    @DisplayName("Excluir um lancamento MANUAL nao registra exclusao")
    void deleteManual() {
        TransactionResponseDTO manual = recordSuccessfully(ana, buy("BTC", "2024-01-10", "0.001", "400000"));

        delete(ana, "/api/v1/transactions/" + manual.id());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_crypto_exclusions", Integer.class)).isZero();
    }
}
