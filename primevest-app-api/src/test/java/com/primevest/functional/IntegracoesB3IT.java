package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.integration.b3investidor.B3InvestidorClient;
import com.primevest.support.ContaSinteticaB3;
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
 * F4) — o cliente HTTP e mockado com a conta inventada {@link ContaSinteticaB3}; conector, regras,
 * pipeline, fila, confirmacao e carteira rodam de verdade.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@TestPropertySource(properties = {
    "b3i.enabled=true",
    "b3i.once-per-day=false",
    "b3i.optin-url=http://localhost:8090/optin"
})
class IntegracoesB3IT extends FuncionalBase {

    private static final String BASE = "/api/v1/integrations/connections";

    @MockBean
    private B3InvestidorClient b3Client;

    private User ana;

    @BeforeEach
    void cenario() {
        ana = usuarioComCarteira("ana@primevest.test");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.primevest.domain.user.UserCpfService userCpfService;

    /** D20: a conexao usa o CPF do perfil. Aqui ele e gravado direto, como Configuracoes faria. */
    private void cpfNoPerfil(User user, String cpf) {
        User u = userRepository.findById(user.getId()).orElseThrow();
        userCpfService.apply(u, userCpfService.normalize(cpf));
        userRepository.save(u);
    }

    private ResponseEntity<Map> conectar(User user, String label, String cpf) {
        cpfNoPerfil(user, cpf);
        return pedirConexao(user, label, cpf);
    }

    private ResponseEntity<Map> pedirConexao(User user, String label, String cpf) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("provider", "B3");
        corpo.put("label", label);
        corpo.put("documentNumber", cpf);
        corpo.put("consentAccepted", true);
        return post(user, BASE, corpo, Map.class);
    }

    private Object conectada(boolean autorizado) {
        ContaSinteticaB3.conta(b3Client, autorizado);
        ResponseEntity<Map> r = conectar(ana, "B3", "529.982.247-25");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody().get("id");
    }

    private String statusDaConexao() {
        return jdbc.queryForObject("SELECT status FROM integration_connections", String.class);
    }

    @Test
    @DisplayName("Com b3i.enabled a B3 aparece entre as fontes, pedindo o CPF")
    void fontes() {
        List<Map<String, Object>> fontes = get(ana, "/api/v1/integrations/sources", List.class).getBody();

        assertThat(fontes).extracting(f -> f.get("provider")).containsExactly("BINANCE", "B3");
        assertThat(fontes.get(1)).containsEntry("credential", "DOCUMENT").containsEntry("oncePerDay", false);
    }

    @Test
    @DisplayName("Conectar com CPF que ainda nao autorizou: PENDING_AUTHORIZATION, com o link de autorizacao")
    void conectarAguardando() {
        ContaSinteticaB3.conta(b3Client, false);

        Map<String, Object> c = conectar(ana, "B3", "529.982.247-25").getBody();

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
    void recusas() {
        ContaSinteticaB3.conta(b3Client, true);

        // D20: sem CPF no perfil nao conecta; CPF diferente do perfil no pedido e recusado.
        assertThat(pedirConexao(ana, "B3 a", ContaSinteticaB3.CPF).getBody().toString()).contains("Cadastre o seu CPF");
        cpfNoPerfil(ana, ContaSinteticaB3.CPF_DESCONHECIDO);
        assertThat(pedirConexao(ana, "B3 a", ContaSinteticaB3.CPF).getBody().toString()).contains("CPF do seu cadastro");

        assertThat(conectar(ana, "B3 b", ContaSinteticaB3.CPF_DESCONHECIDO).getBody().toString()).contains("não localizou");
        assertThat(conectar(ana, "B3 c", ContaSinteticaB3.CPF).getStatusCode()).isEqualTo(HttpStatus.OK);

        // A1: o CPF e unico entre usuarios — o de Ana nao entra no perfil de Bruno.
        User bruno = usuarioComCarteira("bruno@primevest.test");
        Map<String, Object> perfil = new HashMap<>();
        perfil.put("name", "Bruno");
        perfil.put("cpf", ContaSinteticaB3.CPF);
        ResponseEntity<Map> outro = put(bruno, "/api/v1/users/me", perfil, Map.class);
        assertThat(outro.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(outro.getBody().toString()).contains("já está cadastrado em outra conta");

        // C28: com a conexao viva, Ana nao troca nem tira o CPF do perfil.
        Map<String, Object> troca = new HashMap<>();
        troca.put("name", "Ana");
        troca.put("cpf", "");
        ResponseEntity<Map> r = put(ana, "/api/v1/users/me", troca, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().toString()).contains("enquanto houver conexão com a B3");
    }

    @Test
    @DisplayName("Fluxo: aguardando -> autoriza -> importa as negociacoes (B1) -> segunda busca sem nada novo (C4)")
    void fluxoCompleto() {
        Object conexao = conectada(false);

        // Sincronizar antes do opt-in: falha com o motivo e a conexao segue aguardando.
        Map<String, Object> antes = sincronizar(ana, conexao);
        assertThat(antes.get("status")).isEqualTo("FAILED");
        assertThat((String) antes.get("error")).contains("autorizar o PrimeVest");
        assertThat(statusDaConexao()).isEqualTo("PENDING_AUTHORIZATION");

        // O investidor autoriza na B3.
        ContaSinteticaB3.autorizacao(b3Client, true);
        Map<String, Object> p = sincronizar(ana, conexao);
        assertThat(p.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(statusDaConexao()).isEqualTo("ACTIVE");
        assertThat(numero(p.get("newCount"))).isEqualByComparingTo(String.valueOf(ContaSinteticaB3.NOVAS));
        assertThat(numero(p.get("duplicateCount"))).isZero();

        List<Map<String, Object>> itens = (List<Map<String, Object>>) p.get("items");
        Map<String, Object> petr4 = itens.stream()
            .filter(i -> "b3:negociacao:2024-03-04:PETR4:C".equals(i.get("externalId"))).findFirst().orElseThrow();
        Map<String, Object> proposta = (Map<String, Object>) petr4.get("transaction");
        assertThat(numero(proposta.get("shares"))).isEqualByComparingTo("107");
        assertThat(numero(proposta.get("totalAmount"))).isEqualByComparingTo("4119.64");
        assertThat(proposta.get("transactionDate")).isEqualTo("2024-03-04");
        // Conferencia: provento informado pela B3 x proventos da carteira (C3), no motivo.
        assertThat(itens).filteredOn(i -> "CASH_EVENT".equals(i.get("kind")))
            .hasSize(3)
            .allMatch(i -> ((String) i.get("reason")).startsWith("Provento ("));

        Map<String, Object> confirmada = post(ana, BASE + "/" + conexao + "/runs/" + p.get("runId") + "/confirm", null,
            Map.class).getBody();
        assertThat(confirmada.get("status")).isEqualTo("SUCCEEDED");
        assertThat(numero(posicaoDe(ana, "PETR4").get("totalShares"))).isEqualByComparingTo("77");
        assertThat(numero(posicaoDe(ana, "HGLG11").get("totalShares"))).isEqualByComparingTo("10");
        // A Posicao da B3 bate com a carteira: nenhuma divergencia (6.7).
        assertThat((List<?>) confirmada.get("divergences")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE origin = 'AUTO'", Integer.class))
            .isEqualTo(ContaSinteticaB3.NOVAS);

        // C4: a segunda busca (cursor em D-1) nao traz nada novo.
        Map<String, Object> segunda = sincronizar(ana, conexao);
        assertThat(segunda.get("status")).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(numero(segunda.get("newCount"))).isZero();
    }

    @Test
    @DisplayName("C5: o investidor revoga na B3 -> a proxima busca encerra a conexao (REVOKED) e apaga o CPF")
    void revogacao() {
        Object conexao = conectada(true);
        assertThat(statusDaConexao()).isEqualTo("ACTIVE");

        ContaSinteticaB3.autorizacao(b3Client, false);
        Map<String, Object> r = sincronizar(ana, conexao);

        assertThat(r.get("status")).isEqualTo("FAILED");
        assertThat((String) r.get("error")).contains("revogada");
        assertThat(statusDaConexao()).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_credentials", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
    }

    @Test
    @DisplayName("A rotina diaria tambem confere a conexao que aguarda autorizacao (G8)")
    void rotinaConfereAguardando() {
        conectada(false);

        assertThat(integrationService.enqueueScheduled()).isEqualTo(1);
    }
}
