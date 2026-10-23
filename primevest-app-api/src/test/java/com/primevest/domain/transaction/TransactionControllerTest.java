package com.primevest.domain.transaction;

import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import com.primevest.support.MvcTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest extends MvcTestBase {

    @MockBean
    private TransactionService transactionService;

    private static final UUID USER = UUID.fromString(USER_ID);

    private static final String VALID_BUY = """
        {"ticker":"PETR4","type":"BUY","shares":10,"pricePerShare":30.00,"transactionDate":"2026-09-01"}
        """;

    private TransactionResponseDTO response(UUID id) {
        return new TransactionResponseDTO(id, UUID.randomUUID(), "PETR4", "PETROBRAS", 1L, "Ações",
            null, null, TransactionType.BUY, new BigDecimal("10"), new BigDecimal("30.00"),
            new BigDecimal("300.00"), BigDecimal.ZERO, LocalDate.of(2026, 9, 1), LocalDateTime.now(),
            null, null, null, null, null, false, false, TransactionOrigin.MANUAL, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null);
    }

    @Test
    @DisplayName("Sem token, todo endpoint de transacoes responde 401")
    void withoutToken() throws Exception {
        mvc.perform(get("/api/v1/transactions/")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/transactions/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/transactions/").contentType(MediaType.APPLICATION_JSON).content(VALID_BUY))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/transactions/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Criar responde 200 (hoje; ver F12) e usa o usuario do token")
    void create() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.createTransaction(eq(USER), any())).thenReturn(response(id));

        mvc.perform(post("/api/v1/transactions/").contentType(MediaType.APPLICATION_JSON).content(VALID_BUY))
            // Hoje o POST responde 200, e nao 201. Registrado como F12 no
            // docs/planos/PLANO_TESTES.md: mudar o status e decisao de contrato de API.
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticker").value("PETR4"))
            .andExpect(jsonPath("$.assetTypeName").value("Ações"));

        verify(transactionService).createTransaction(eq(USER), any());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Listar responde 200 com a lista")
    void list() throws Exception {
        when(transactionService.getTransactions(USER)).thenReturn(List.of(response(UUID.randomUUID())));

        mvc.perform(get("/api/v1/transactions/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].ticker").value("PETR4"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Transacao de outro dono responde 404, nunca 403")
    void fromOtherOwner() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.getTransactionById(USER, id))
            .thenThrow(new ResourceNotFoundException("Transação não encontrada"));

        mvc.perform(get("/api/v1/transactions/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Transação não encontrada"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Regra de negocio recusada responde 422 com a mensagem do dominio")
    void businessRule() throws Exception {
        when(transactionService.createTransaction(eq(USER), any()))
            .thenThrow(new BusinessException("Não há posição de PETR4 na carteira para vender."));

        mvc.perform(post("/api/v1/transactions/").contentType(MediaType.APPLICATION_JSON).content(VALID_BUY))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Não há posição de PETR4 na carteira para vender."));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Ticker vazio, tipo ausente e data ausente respondem 400, com os campos apontados")
    void fieldValidation() throws Exception {
        mvc.perform(post("/api/v1/transactions/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticker\":\"\",\"shares\":10,\"pricePerShare\":30.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Dados inválidos"))
            .andExpect(jsonPath("$.errors.ticker").exists())
            .andExpect(jsonPath("$.errors.type").exists())
            .andExpect(jsonPath("$.errors.transactionDate").exists());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Quantidade e preco negativos ou zero respondem 400 (@Positive)")
    void nonPositiveValues() throws Exception {
        mvc.perform(post("/api/v1/transactions/").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ticker":"PETR4","type":"BUY","shares":0,"pricePerShare":-1,"transactionDate":"2026-09-01"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.shares").exists())
            .andExpect(jsonPath("$.errors.pricePerShare").exists());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F9 — JSON malformado responde 400, e nao 500")
    void malformedJson() throws Exception {
        mvc.perform(post("/api/v1/transactions/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticker\": \"PETR4\", "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Corpo da requisição inválido"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F9 — tipo de transacao inexistente responde 400, e nao 500")
    void invalidEnum() throws Exception {
        mvc.perform(post("/api/v1/transactions/").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ticker":"PETR4","type":"DOACAO","shares":10,"pricePerShare":30,"transactionDate":"2026-09-01"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Editar responde 200 e excluir responde 204")
    void editAndDelete() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.updateTransaction(eq(USER), eq(id), any())).thenReturn(response(id));

        mvc.perform(put("/api/v1/transactions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BUY))
            .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/transactions/{id}", id))
            .andExpect(status().isNoContent());

        verify(transactionService).deleteTransaction(USER, id);
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F9 — metodo nao suportado responde 405, e nao 500")
    void methodNotSupported() throws Exception {
        mvc.perform(delete("/api/v1/transactions/"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.message").value("Método não permitido"));
    }
}
