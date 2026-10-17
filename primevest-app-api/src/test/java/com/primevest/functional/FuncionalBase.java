package com.primevest.functional;

import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.domain.user.User;
import com.primevest.support.IntegrationTestBase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Atalhos que todos os testes funcionais usam: lancar transacao e ler a
 * carteira pela API, do jeito que a tela faz.
 */
public abstract class FuncionalBase extends IntegrationTestBase {

    /** Binance mockada no cliente HTTP: conector, regras e API rodam de verdade. */
    @org.springframework.boot.test.mock.mockito.MockBean
    protected com.primevest.integration.binance.BinanceClient binanceClient;

    @org.springframework.beans.factory.annotation.Autowired
    protected com.primevest.domain.integration.IntegrationService integrationService;

    /**
     * E-mails da conta (docs/planos/PLANO_CADASTRO_USUARIO.md, U3): nenhum sai do teste. Os testes de
     * cadastro leem o codigo enviado deste mock.
     */
    @org.springframework.boot.test.mock.mockito.MockBean
    protected com.primevest.domain.auth.mail.AccountMailer accountMailer;

    @org.springframework.beans.factory.annotation.Autowired
    private com.primevest.domain.auth.verification.VerificationStore verificationStore;

    /** Codigos e contadores de tentativa em memoria: cada cenario comeca sem os do anterior. */
    @org.junit.jupiter.api.BeforeEach
    void limparCodigos() {
        if (verificationStore instanceof com.primevest.domain.auth.verification.InMemoryVerificationStore m) {
            m.clear();
        }
    }

    /**
     * Pede a sincronizacao pela API (202, fila), roda o worker como a rotina
     * faria e devolve a execucao como a tela a leria.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> sincronizar(User user, Object conexao) {
        ResponseEntity<Map> pedido = post(user, "/api/v1/integrations/connections/" + conexao + "/preview", null, Map.class);
        assertThat(pedido.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        while (integrationService.processNext()) {
            // esvazia a fila
        }
        return get(user, "/api/v1/integrations/connections/" + conexao + "/runs/" + pedido.getBody().get("runId"),
            Map.class).getBody();
    }

    protected Map<String, Object> compra(String ticker, String data, String cotas, String preco) {
        return lancamento(ticker, "BUY", data, cotas, preco);
    }

    protected Map<String, Object> venda(String ticker, String data, String cotas, String preco) {
        return lancamento(ticker, "SELL", data, cotas, preco);
    }

    protected Map<String, Object> lancamento(String ticker, String tipo, String data, String cotas, String preco) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("ticker", ticker);
        corpo.put("type", tipo);
        corpo.put("transactionDate", data);
        corpo.put("shares", new BigDecimal(cotas));
        corpo.put("pricePerShare", new BigDecimal(preco));
        // Tesouro Direto: compra e venda informam a distribuidora (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R13)
        if (ticker.startsWith("Tesouro")) {
            corpo.put("distributorId", distribuidoraPadrao());
        }
        return corpo;
    }

    /** A corretora dos testes (emissoras de renda fixa): criada uma vez, achada pela raiz do CNPJ. */
    protected Long distribuidoraPadrao() {
        return empresaDeTeste("Corretora de Teste", "CORRETORA_DISTRIBUIDORA");
    }

    /** Empresa de renda fixa de teste pelo nome ("99" + 6 digitos do nome como raiz do CNPJ). */
    protected Long empresaDeTeste(String nome, String tipo) {
        String raiz = "99" + String.format("%06d", Math.floorMod(nome.hashCode(), 1_000_000));
        List<Long> ids = jdbc.queryForList("SELECT id FROM fixed_income_issuers WHERE cnpj_root = ?", Long.class, raiz);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject("""
            INSERT INTO fixed_income_issuers (kind, cnpj_root, legal_name, status, guarantor)
            VALUES (?, ?, ?, 'ATIVA', CASE WHEN ? IN ('BANCO', 'FINANCEIRA') THEN 'FGC' ELSE 'NENHUM' END)
            RETURNING id""", Long.class, tipo, raiz, nome, tipo);
    }

    /** Aplicacao em renda fixa: valor total e os dados do titulo. */
    protected Map<String, Object> aplicacaoEmCdb(String ticker, String data, String valor, String emissor) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("ticker", ticker);
        corpo.put("type", "BUY");
        corpo.put("transactionDate", data);
        corpo.put("totalAmount", new BigDecimal(valor));
        corpo.put("issuerId", empresaDeTeste(emissor, "BANCO"));
        corpo.put("distributorId", distribuidoraPadrao());
        corpo.put("maturityDate", "2030-01-15");
        corpo.put("rateType", "POS_FIXADO");
        corpo.put("indexer", "CDI");
        corpo.put("indexerRate", new BigDecimal("110.0000"));
        return corpo;
    }

    protected ResponseEntity<TransactionResponseDTO> lancar(User user, Map<String, Object> corpo) {
        return post(user, "/api/v1/transactions/", corpo, TransactionResponseDTO.class);
    }

    protected TransactionResponseDTO lancarComSucesso(User user, Map<String, Object> corpo) {
        ResponseEntity<TransactionResponseDTO> resposta = lancar(user, corpo);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    protected ResponseEntity<String> lancarEsperandoErro(User user, Map<String, Object> corpo) {
        return post(user, "/api/v1/transactions/", corpo, String.class);
    }

    protected List<TransactionResponseDTO> transacoesDe(User user) {
        return List.of(get(user, "/api/v1/transactions/", TransactionResponseDTO[].class).getBody());
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> carteiraDe(User user) {
        return List.of(get(user, "/api/v1/portfolio/assets", Map[].class).getBody())
            .stream().map(m -> (Map<String, Object>) m).toList();
    }

    protected Map<String, Object> posicaoDe(User user, String ticker) {
        return carteiraDe(user).stream()
            .filter(linha -> ticker.equals(linha.get("ticker")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Posição não encontrada na carteira: " + ticker));
    }

    protected BigDecimal numero(Object valor) {
        return new BigDecimal(String.valueOf(valor));
    }

    /** Liga a reserva de emergencia de uma posicao, como o switch da Carteira. */
    protected ResponseEntity<String> marcarComoReserva(User user, Object positionId, boolean reserva) {
        return rest.exchange("/api/v1/portfolio/assets/" + positionId + "/reservation",
            org.springframework.http.HttpMethod.PATCH,
            entidadeJson(user, Map.of("isReservation", reserva)), String.class);
    }

    private org.springframework.http.HttpEntity<Object> entidadeJson(User user, Object corpo) {
        var headers = headers(user);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(corpo, headers);
    }
}
