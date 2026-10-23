package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.support.BinanceFake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracao com a Binance pela API, do jeito que qualquer usuario (e a tela
 * de Integracoes, F6) vai usar — com a Binance mockada no cliente HTTP e uma
 * conta ficticia ({@link BinanceFake}). docs/planos/PLANO_CRIPTO_BINANCE.md, F3.
 */
class IntegrationsIT extends FunctionalBase {

    private static final String BASE = "/api/v1/integrations/connections";

    private User ana;
    private User bruno;

    @BeforeEach
    void scenario() {
        ana = userWithPortfolio("ana@primevest.test");
        bruno = userWithPortfolio("bruno@primevest.test");
    }

    private Map<String, Object> connection(String nickname) {
        Map<String, Object> body = new HashMap<>();
        body.put("provider", "BINANCE");
        body.put("label", nickname);
        body.put("apiKey", "chave-ficticia-de-teste-AB12");
        body.put("apiSecret", "segredo-ficticio-de-teste");
        body.put("consentAccepted", true);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> connect(User user, String nickname) {
        ResponseEntity<Map> r = post(user, BASE, connection(nickname), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @Test
    @DisplayName("Sem b3i.enabled a B3 nao aparece entre as fontes nem conecta")
    @SuppressWarnings("unchecked")
    void b3Disabled() {
        List<Map<String, Object>> sources = get(ana, "/api/v1/integrations/sources", List.class).getBody();
        assertThat(sources).extracting(f -> f.get("provider")).containsExactly("BINANCE");
        Map<String, Object> body = new HashMap<>();
        body.put("provider", "B3");
        body.put("label", "B3");
        body.put("documentNumber", "52998224725");
        body.put("consentAccepted", true);
        assertThat(post(ana, BASE, body, String.class).getBody()).contains("não suportada");
    }

    private Map<String, Object> preview(User user, Object id) {
        Map<String, Object> run = sync(user, id);
        assertThat(run.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        return run;
    }

    @Test
    @DisplayName("Sem token: 401")
    void withoutToken() {
        ResponseEntity<String> r = rest.exchange(BASE, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Conectar: confere a chave, grava cifrada e devolve so os 4 ultimos caracteres")
    void connect() {
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);

        Map<String, Object> c = connect(ana, "Binance principal");

        assertThat(c.get("status")).isEqualTo("ACTIVE");
        assertThat(c.get("fingerprint")).isEqualTo("AB12");
        assertThat(c.get("validatedAt")).isNotNull();
        assertThat(c.toString()).doesNotContain("segredo").doesNotContain("chave-ficticia");

        byte[] encrypted = jdbc.queryForObject("SELECT ciphertext FROM integration_credentials", byte[].class);
        assertThat(new String(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("segredo-ficticio");
        assertThat(jdbc.queryForObject("SELECT permissions->>'enableReading' FROM integration_credentials", String.class))
            .isEqualTo("true");
    }

    @Test
    @DisplayName("Chave que negocia e recusada (422) e nada e gravado")
    void tradingKey() {
        BinanceFake.account(binanceClient, BinanceFake.TRADES);

        ResponseEntity<String> r = post(ana, BASE, connection("Binance"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).contains("negociar");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_connections", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Chave recusada pela Binance: 422 com orientacao, nada gravado")
    void rejectedKey() {
        BinanceFake.rejectedKey(binanceClient);

        ResponseEntity<String> r = post(ana, BASE, connection("Binance"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).contains("recusou");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_connections", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sem aceitar o consentimento: 400")
    void withoutConsent() {
        Map<String, Object> body = connection("Binance");
        body.put("consentAccepted", false);

        assertThat(post(ana, BASE, body, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Apelido repetido para o mesmo usuario: 422; outro usuario pode usar o mesmo apelido")
    void repeatedNickname() {
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        connect(ana, "Binance principal");

        assertThat(post(ana, BASE, connection("binance PRINCIPAL"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        connect(bruno, "Binance principal");
    }

    @Test
    @DisplayName("Pre-visualizacao pela API: 3 novas, 2 nao importadas com motivo, 1.000,00 depositados — nada gravado")
    @SuppressWarnings("unchecked")
    void previewRun() {
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        Object id = connect(ana, "Binance principal").get("id");

        Map<String, Object> p = preview(ana, id);

        assertThat(number(p.get("newCount"))).isEqualByComparingTo("3");
        assertThat(number(p.get("unmappedCount"))).isEqualByComparingTo("2");
        assertThat(number(p.get("fiatDeposited"))).isEqualByComparingTo("1000.00");

        List<Map<String, Object>> runItemList = (List<Map<String, Object>>) p.get("items");
        Map<String, Object> order = runItemList.stream()
            .filter(i -> "binance:spot:BTCBRL:5001".equals(i.get("externalId"))).findFirst().orElseThrow();
        Map<String, Object> t = (Map<String, Object>) order.get("transaction");
        assertThat(number(t.get("shares"))).isEqualByComparingTo("0.001998");
        assertThat(number(t.get("pricePerShare"))).isEqualByComparingTo("400050");
        assertThat(number(t.get("brokerageFee"))).isEqualByComparingTo("0.80");
        assertThat(number(t.get("totalAmount")).add(number(t.get("brokerageFee")))).isEqualByComparingTo("800.10");
        assertThat(t.get("transactionDate")).isEqualTo("2024-02-02");

        assertThat(runItemList).filteredOn(i -> "UNMAPPED".equals(i.get("outcome")))
            .extracting(i -> i.get("externalId"))
            .containsExactlyInAnyOrder("binance:convert:C1", "binance:deposit:D1");
        assertThat(runItemList).filteredOn(i -> "UNMAPPED".equals(i.get("outcome")))
            .allSatisfy(i -> assertThat((String) i.get("reason")).isNotBlank());

        // Pre-visualizar nao grava transacao nem cursor.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_cursors", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Dois usuarios: um nao lista, nao pre-visualiza nem desconecta a conexao do outro (404)")
    void isolationBetweenUsers() {
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        Object anasPortfolio = connect(ana, "Binance da Ana").get("id");

        assertThat(get(bruno, BASE, List.class).getBody()).isEmpty();
        assertThat(post(bruno, BASE + "/" + anasPortfolio + "/preview", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(bruno, BASE + "/" + anasPortfolio).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(ana, BASE, List.class).getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Chave que passou a negociar depois de conectada: a busca falha com o motivo e marca a conexao")
    void permissionChangedLater() {
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        Object id = connect(ana, "Binance").get("id");

        BinanceFake.account(binanceClient, BinanceFake.TRADES);
        Map<String, Object> run = sync(ana, id);

        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat((String) run.get("error")).contains("negociar");
        assertThat(jdbc.queryForObject("SELECT status FROM integration_connections", String.class))
            .isEqualTo("PERMISSION_TOO_BROAD");
    }

    @Test
    @DisplayName("Desconectar apaga a credencial, marca REVOKED e bloqueia a pre-visualizacao")
    void disconnect() {
        BinanceFake.account(binanceClient, BinanceFake.READ_ONLY);
        Object id = connect(ana, "Binance").get("id");

        assertThat(delete(ana, BASE + "/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_credentials", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM integration_connections", String.class)).isEqualTo("REVOKED");
        assertThat(post(ana, BASE + "/" + id + "/preview", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
