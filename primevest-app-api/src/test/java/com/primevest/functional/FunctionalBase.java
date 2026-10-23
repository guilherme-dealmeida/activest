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
public abstract class FunctionalBase extends IntegrationTestBase {

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
    void clearCodes() {
        if (verificationStore instanceof com.primevest.domain.auth.verification.InMemoryVerificationStore m) {
            m.clear();
        }
    }

    /**
     * Pede a sincronizacao pela API (202, fila), roda o worker como a rotina
     * faria e devolve a execucao como a tela a leria.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> sync(User user, Object connection) {
        ResponseEntity<Map> request = post(user, "/api/v1/integrations/connections/" + connection + "/preview", null, Map.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        while (integrationService.processNext()) {
            // esvazia a fila
        }
        return get(user, "/api/v1/integrations/connections/" + connection + "/runs/" + request.getBody().get("runId"),
            Map.class).getBody();
    }

    protected Map<String, Object> buy(String ticker, String data, String shares, String price) {
        return entry(ticker, "BUY", data, shares, price);
    }

    protected Map<String, Object> sale(String ticker, String data, String shares, String price) {
        return entry(ticker, "SELL", data, shares, price);
    }

    protected Map<String, Object> entry(String ticker, String type, String data, String shares, String price) {
        Map<String, Object> body = new HashMap<>();
        body.put("ticker", ticker);
        body.put("type", type);
        body.put("transactionDate", data);
        body.put("shares", new BigDecimal(shares));
        body.put("pricePerShare", new BigDecimal(price));
        // Tesouro Direto: compra e venda informam a distribuidora (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R13)
        if (ticker.startsWith("Tesouro")) {
            body.put("distributorId", defaultDistributor());
        }
        return body;
    }

    /** A corretora dos testes (emissoras de renda fixa): criada uma vez, achada pela raiz do CNPJ. */
    protected Long defaultDistributor() {
        return testCompany("Corretora de Teste", "CORRETORA_DISTRIBUIDORA");
    }

    /** Empresa de renda fixa de teste pelo nome ("99" + 6 digitos do nome como raiz do CNPJ). */
    protected Long testCompany(String name, String type) {
        String root = "99" + String.format("%06d", Math.floorMod(name.hashCode(), 1_000_000));
        List<Long> ids = jdbc.queryForList("SELECT id FROM fixed_income_issuers WHERE cnpj_root = ?", Long.class, root);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject("""
            INSERT INTO fixed_income_issuers (kind, cnpj_root, legal_name, status, guarantor)
            VALUES (?, ?, ?, 'ATIVA', CASE WHEN ? IN ('BANCO', 'FINANCEIRA') THEN 'FGC' ELSE 'NENHUM' END)
            RETURNING id""", Long.class, type, root, name, type);
    }

    /** Aplicacao em renda fixa: valor total e os dados do titulo. */
    protected Map<String, Object> cdbInvestment(String ticker, String data, String value, String issuer) {
        Map<String, Object> body = new HashMap<>();
        body.put("ticker", ticker);
        body.put("type", "BUY");
        body.put("transactionDate", data);
        body.put("totalAmount", new BigDecimal(value));
        body.put("issuerId", testCompany(issuer, "BANCO"));
        body.put("distributorId", defaultDistributor());
        body.put("maturityDate", "2030-01-15");
        body.put("rateType", "POS_FIXADO");
        body.put("indexer", "CDI");
        body.put("indexerRate", new BigDecimal("110.0000"));
        return body;
    }

    protected ResponseEntity<TransactionResponseDTO> record(User user, Map<String, Object> body) {
        return post(user, "/api/v1/transactions/", body, TransactionResponseDTO.class);
    }

    protected TransactionResponseDTO recordSuccessfully(User user, Map<String, Object> body) {
        ResponseEntity<TransactionResponseDTO> response = record(user, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    protected ResponseEntity<String> recordExpectingError(User user, Map<String, Object> body) {
        return post(user, "/api/v1/transactions/", body, String.class);
    }

    protected List<TransactionResponseDTO> transactionsOf(User user) {
        return List.of(get(user, "/api/v1/transactions/", TransactionResponseDTO[].class).getBody());
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> portfolioOf(User user) {
        return List.of(get(user, "/api/v1/portfolio/assets", Map[].class).getBody())
            .stream().map(m -> (Map<String, Object>) m).toList();
    }

    protected Map<String, Object> positionOf(User user, String ticker) {
        return portfolioOf(user).stream()
            .filter(row -> ticker.equals(row.get("ticker")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Posição não encontrada na carteira: " + ticker));
    }

    protected BigDecimal number(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    /** Liga a reserva de emergencia de uma posicao, como o switch da Carteira. */
    protected ResponseEntity<String> markAsReserve(User user, Object positionId, boolean reserve) {
        return rest.exchange("/api/v1/portfolio/assets/" + positionId + "/reservation",
            org.springframework.http.HttpMethod.PATCH,
            entityJson(user, Map.of("isReservation", reserve)), String.class);
    }

    private org.springframework.http.HttpEntity<Object> entityJson(User user, Object body) {
        var headers = headers(user);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
