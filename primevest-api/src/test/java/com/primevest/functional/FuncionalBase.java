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
        return corpo;
    }

    /** Aplicacao em renda fixa: valor total e os dados do titulo. */
    protected Map<String, Object> aplicacaoEmCdb(String ticker, String data, String valor, String emissor) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("ticker", ticker);
        corpo.put("type", "BUY");
        corpo.put("transactionDate", data);
        corpo.put("totalAmount", new BigDecimal(valor));
        corpo.put("issuer", emissor);
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
