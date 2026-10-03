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
class IntegracoesIT extends FuncionalBase {

    private static final String BASE = "/api/v1/integrations/connections";

    private User ana;
    private User bruno;

    @BeforeEach
    void cenario() {
        ana = usuarioComCarteira("ana@primevest.test");
        bruno = usuarioComCarteira("bruno@primevest.test");
    }

    private Map<String, Object> conexao(String apelido) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("provider", "BINANCE");
        corpo.put("label", apelido);
        corpo.put("apiKey", "chave-ficticia-de-teste-AB12");
        corpo.put("apiSecret", "segredo-ficticio-de-teste");
        corpo.put("consentAccepted", true);
        return corpo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> conectar(User user, String apelido) {
        ResponseEntity<Map> r = post(user, BASE, conexao(apelido), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private Map<String, Object> preview(User user, Object id) {
        Map<String, Object> run = sincronizar(user, id);
        assertThat(run.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        return run;
    }

    @Test
    @DisplayName("Sem token: 401")
    void semToken() {
        ResponseEntity<String> r = rest.exchange(BASE, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Conectar: confere a chave, grava cifrada e devolve so os 4 ultimos caracteres")
    void conectar() {
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);

        Map<String, Object> c = conectar(ana, "Binance principal");

        assertThat(c.get("status")).isEqualTo("ACTIVE");
        assertThat(c.get("fingerprint")).isEqualTo("AB12");
        assertThat(c.get("validatedAt")).isNotNull();
        assertThat(c.toString()).doesNotContain("segredo").doesNotContain("chave-ficticia");

        byte[] cifrado = jdbc.queryForObject("SELECT ciphertext FROM integration_credentials", byte[].class);
        assertThat(new String(cifrado, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("segredo-ficticio");
        assertThat(jdbc.queryForObject("SELECT permissions->>'enableReading' FROM integration_credentials", String.class))
            .isEqualTo("true");
    }

    @Test
    @DisplayName("Chave que negocia e recusada (422) e nada e gravado")
    void chaveQueNegocia() {
        BinanceFake.conta(binanceClient, BinanceFake.NEGOCIA);

        ResponseEntity<String> r = post(ana, BASE, conexao("Binance"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).contains("negociar");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_connections", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Chave recusada pela Binance: 422 com orientacao, nada gravado")
    void chaveRecusada() {
        BinanceFake.chaveRecusada(binanceClient);

        ResponseEntity<String> r = post(ana, BASE, conexao("Binance"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).contains("recusou");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_connections", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sem aceitar o consentimento: 400")
    void semConsentimento() {
        Map<String, Object> corpo = conexao("Binance");
        corpo.put("consentAccepted", false);

        assertThat(post(ana, BASE, corpo, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Apelido repetido para o mesmo usuario: 422; outro usuario pode usar o mesmo apelido")
    void apelidoRepetido() {
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        conectar(ana, "Binance principal");

        assertThat(post(ana, BASE, conexao("binance PRINCIPAL"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        conectar(bruno, "Binance principal");
    }

    @Test
    @DisplayName("Pre-visualizacao pela API: 3 novas, 2 nao importadas com motivo, 1.000,00 depositados — nada gravado")
    @SuppressWarnings("unchecked")
    void preVisualizacao() {
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        Object id = conectar(ana, "Binance principal").get("id");

        Map<String, Object> p = preview(ana, id);

        assertThat(numero(p.get("newCount"))).isEqualByComparingTo("3");
        assertThat(numero(p.get("unmappedCount"))).isEqualByComparingTo("2");
        assertThat(numero(p.get("fiatDeposited"))).isEqualByComparingTo("1000.00");

        List<Map<String, Object>> itens = (List<Map<String, Object>>) p.get("items");
        Map<String, Object> ordem = itens.stream()
            .filter(i -> "binance:spot:BTCBRL:5001".equals(i.get("externalId"))).findFirst().orElseThrow();
        Map<String, Object> t = (Map<String, Object>) ordem.get("transaction");
        assertThat(numero(t.get("shares"))).isEqualByComparingTo("0.001998");
        assertThat(numero(t.get("pricePerShare"))).isEqualByComparingTo("400050");
        assertThat(numero(t.get("brokerageFee"))).isEqualByComparingTo("0.80");
        assertThat(numero(t.get("totalAmount")).add(numero(t.get("brokerageFee")))).isEqualByComparingTo("800.10");
        assertThat(t.get("transactionDate")).isEqualTo("2024-02-02");

        assertThat(itens).filteredOn(i -> "UNMAPPED".equals(i.get("outcome")))
            .extracting(i -> i.get("externalId"))
            .containsExactlyInAnyOrder("binance:convert:C1", "binance:deposit:D1");
        assertThat(itens).filteredOn(i -> "UNMAPPED".equals(i.get("outcome")))
            .allSatisfy(i -> assertThat((String) i.get("reason")).isNotBlank());

        // Pre-visualizar nao grava transacao nem cursor.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_cursors", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Dois usuarios: um nao lista, nao pre-visualiza nem desconecta a conexao do outro (404)")
    void isolamentoEntreUsuarios() {
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        Object daAna = conectar(ana, "Binance da Ana").get("id");

        assertThat(get(bruno, BASE, List.class).getBody()).isEmpty();
        assertThat(post(bruno, BASE + "/" + daAna + "/preview", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(bruno, BASE + "/" + daAna).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(ana, BASE, List.class).getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Chave que passou a negociar depois de conectada: a busca falha com o motivo e marca a conexao")
    void permissaoMudouDepois() {
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        Object id = conectar(ana, "Binance").get("id");

        BinanceFake.conta(binanceClient, BinanceFake.NEGOCIA);
        Map<String, Object> run = sincronizar(ana, id);

        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat((String) run.get("error")).contains("negociar");
        assertThat(jdbc.queryForObject("SELECT status FROM integration_connections", String.class))
            .isEqualTo("PERMISSION_TOO_BROAD");
    }

    @Test
    @DisplayName("Desconectar apaga a credencial, marca REVOKED e bloqueia a pre-visualizacao")
    void desconectar() {
        BinanceFake.conta(binanceClient, BinanceFake.SO_LEITURA);
        Object id = conectar(ana, "Binance").get("id");

        assertThat(delete(ana, BASE + "/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_credentials", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM integration_connections", String.class)).isEqualTo("REVOKED");
        assertThat(post(ana, BASE + "/" + id + "/preview", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
