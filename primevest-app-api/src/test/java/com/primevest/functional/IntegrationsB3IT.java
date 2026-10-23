package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.integration.b3investidor.B3InvestidorClient;
import com.primevest.support.SyntheticAccountB3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracao com a Area do Investidor da B3 de ponta a ponta na API (docs/planos/PLANO_INTEGRACAO_B3.md,
 * F4) — o cliente HTTP e mockado com a conta inventada {@link SyntheticAccountB3}; conector, regras,
 * pipeline, fila, confirmacao e carteira rodam de verdade.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@TestPropertySource(properties = {
    "b3i.enabled=true",
    "b3i.once-per-day=false",
    "b3i.optin-url=http://localhost:8090/optin"
})
class IntegrationsB3IT extends FunctionalBase {

    private static final String BASE = "/api/v1/integrations/connections";

    @MockBean
    private B3InvestidorClient b3Client;

    private User ana;

    @BeforeEach
    void scenario() {
        ana = userWithPortfolio("ana@primevest.test");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.primevest.domain.user.UserCpfService userCpfService;

    /** D20: a conexao usa o CPF do perfil. Aqui ele e gravado direto, como Configuracoes faria. */
    private void cpfInProfile(User user, String cpf) {
        User u = userRepository.findById(user.getId()).orElseThrow();
        userCpfService.apply(u, userCpfService.normalize(cpf));
        userRepository.save(u);
    }

    private ResponseEntity<Map> connect(User user, String label, String cpf) {
        cpfInProfile(user, cpf);
        return requestConnection(user, label, cpf);
    }

    private ResponseEntity<Map> requestConnection(User user, String label, String cpf) {
        Map<String, Object> body = new HashMap<>();
        body.put("provider", "B3");
        body.put("label", label);
        body.put("documentNumber", cpf);
        body.put("consentAccepted", true);
        return post(user, BASE, body, Map.class);
    }

    private Object connected(boolean authorized) {
        SyntheticAccountB3.account(b3Client, authorized);
        ResponseEntity<Map> r = connect(ana, "B3", "529.982.247-25");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody().get("id");
    }

    private String connectionStatus() {
        return jdbc.queryForObject("SELECT status FROM integration_connections", String.class);
    }

    @Test
    @DisplayName("Com b3i.enabled a B3 aparece entre as fontes, pedindo o CPF")
    void sources() {
        List<Map<String, Object>> sources = get(ana, "/api/v1/integrations/sources", List.class).getBody();

        assertThat(sources).extracting(f -> f.get("provider")).containsExactly("BINANCE", "B3");
        assertThat(sources.get(1)).containsEntry("credential", "DOCUMENT").containsEntry("oncePerDay", false);
    }

    @Test
    @DisplayName("Conectar com CPF que ainda nao autorizou: PENDING_AUTHORIZATION, com o link de autorizacao")
    void connectAwaiting() {
        SyntheticAccountB3.account(b3Client, false);

        Map<String, Object> c = connect(ana, "B3", "529.982.247-25").getBody();

        assertThat(c.get("status")).isEqualTo("PENDING_AUTHORIZATION");
        assertThat(c.get("authorizationUrl")).isEqualTo("http://localhost:8090/optin");
        assertThat(c.get("fingerprint")).isEqualTo("25");
        assertThat((String) c.get("lastError")).contains("autorizar o PrimeVest");
        // O CPF fica cifrado: nem o numero nem os digitos aparecem em claro.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_credentials WHERE "
            + "position(convert_to('52998224725', 'UTF8') in ciphertext) > 0", Integer.class)).isZero();
    }

    @Test
    @DisplayName("D20: sem CPF no perfil, CPF diferente do perfil, CPF que a B3 nao localiza, CPF de outro usuario (A1) e troca de CPF com conexao viva sao recusados")
    void rejections() {
        SyntheticAccountB3.account(b3Client, true);

        // D20: sem CPF no perfil nao conecta; CPF diferente do perfil no pedido e recusado.
        assertThat(requestConnection(ana, "B3 a", SyntheticAccountB3.CPF).getBody().toString()).contains("Cadastre o seu CPF");
        cpfInProfile(ana, SyntheticAccountB3.UNKNOWN_CPF);
        assertThat(requestConnection(ana, "B3 a", SyntheticAccountB3.CPF).getBody().toString()).contains("CPF do seu cadastro");

        assertThat(connect(ana, "B3 b", SyntheticAccountB3.UNKNOWN_CPF).getBody().toString()).contains("não localizou");
        assertThat(connect(ana, "B3 c", SyntheticAccountB3.CPF).getStatusCode()).isEqualTo(HttpStatus.OK);

        // A1: o CPF e unico entre usuarios — o de Ana nao entra no perfil de Bruno.
        User bruno = userWithPortfolio("bruno@primevest.test");
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", "Bruno");
        profile.put("cpf", SyntheticAccountB3.CPF);
        ResponseEntity<Map> other = put(bruno, "/api/v1/users/me", profile, Map.class);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(other.getBody().toString()).contains("já está cadastrado em outra conta");

        // C28: com a conexao viva, Ana nao troca nem tira o CPF do perfil.
        Map<String, Object> swap = new HashMap<>();
        swap.put("name", "Ana");
        swap.put("cpf", "");
        ResponseEntity<Map> r = put(ana, "/api/v1/users/me", swap, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().toString()).contains("enquanto houver conexão com a B3");
    }

    @Test
    @DisplayName("Fluxo: aguardando -> autoriza -> importa as negociacoes (B1) -> segunda busca sem nada novo (C4)")
    void fullFlow() {
        Object connection = connected(false);

        // Sincronizar antes do opt-in: falha com o motivo e a conexao segue aguardando.
        Map<String, Object> before = sync(ana, connection);
        assertThat(before.get("status")).isEqualTo("FAILED");
        assertThat((String) before.get("error")).contains("autorizar o PrimeVest");
        assertThat(connectionStatus()).isEqualTo("PENDING_AUTHORIZATION");

        // O investidor autoriza na B3.
        SyntheticAccountB3.authorization(b3Client, true);
        Map<String, Object> p = sync(ana, connection);
        assertThat(p.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(connectionStatus()).isEqualTo("ACTIVE");
        assertThat(number(p.get("newCount"))).isEqualByComparingTo(String.valueOf(SyntheticAccountB3.NEW_ONES));
        assertThat(number(p.get("duplicateCount"))).isZero();

        List<Map<String, Object>> runItemList = (List<Map<String, Object>>) p.get("items");
        Map<String, Object> petr4 = runItemList.stream()
            .filter(i -> "b3:negociacao:2024-03-04:PETR4:C".equals(i.get("externalId"))).findFirst().orElseThrow();
        Map<String, Object> proposed = (Map<String, Object>) petr4.get("transaction");
        assertThat(number(proposed.get("shares"))).isEqualByComparingTo("107");
        assertThat(number(proposed.get("totalAmount"))).isEqualByComparingTo("4119.64");
        assertThat(proposed.get("transactionDate")).isEqualTo("2024-03-04");
        // Conferencia: provento informado pela B3 x proventos da carteira (C3), no motivo.
        assertThat(runItemList).filteredOn(i -> "CASH_EVENT".equals(i.get("kind")))
            .hasSize(3)
            .allMatch(i -> ((String) i.get("reason")).startsWith("Provento ("));

        Map<String, Object> confirmed = post(ana, BASE + "/" + connection + "/runs/" + p.get("runId") + "/confirm", null,
            Map.class).getBody();
        assertThat(confirmed.get("status")).isEqualTo("SUCCEEDED");
        assertThat(number(positionOf(ana, "PETR4").get("totalShares"))).isEqualByComparingTo("77");
        assertThat(number(positionOf(ana, "HGLG11").get("totalShares"))).isEqualByComparingTo("10");
        // A Posicao da B3 bate com a carteira: nenhuma divergencia (6.7).
        assertThat((List<?>) confirmed.get("divergences")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE origin = 'AUTO'", Integer.class))
            .isEqualTo(SyntheticAccountB3.NEW_ONES);

        // C4: a segunda busca (cursor em D-1) nao traz nada novo.
        Map<String, Object> second = sync(ana, connection);
        assertThat(second.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(number(second.get("newCount"))).isZero();
    }

    @Test
    @DisplayName("C5: o investidor revoga na B3 -> a proxima busca encerra a conexao (REVOKED) e apaga o CPF")
    void revocation() {
        Object connection = connected(true);
        assertThat(connectionStatus()).isEqualTo("ACTIVE");

        SyntheticAccountB3.authorization(b3Client, false);
        Map<String, Object> r = sync(ana, connection);

        assertThat(r.get("status")).isEqualTo("FAILED");
        assertThat((String) r.get("error")).contains("revogada");
        assertThat(connectionStatus()).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_credentials", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
    }

    @Test
    @DisplayName("A rotina diaria tambem confere a conexao que aguarda autorizacao (G8)")
    void taskChecksAwaiting() {
        connected(false);

        assertThat(integrationService.enqueueScheduled()).isEqualTo(1);
    }
}
