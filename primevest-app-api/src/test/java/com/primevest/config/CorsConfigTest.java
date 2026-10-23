package com.primevest.config;

import com.primevest.domain.transaction.TransactionController;
import com.primevest.domain.transaction.TransactionService;
import com.primevest.support.MvcTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS do front em desenvolvimento: localhost e 127.0.0.1 valem igual.
 *
 * <p>O preflight (OPTIONS) e o que o navegador manda antes de um POST com JSON
 * vindo de outra origem; recusado ali, o POST nem sai.</p>
 */
@WebMvcTest(TransactionController.class)
@Import(CorsConfig.class)
class CorsConfigTest extends MvcTestBase {

    @MockBean
    private TransactionService transactionService;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "http://localhost:5173", "http://127.0.0.1:5173",
        "http://localhost:5174", "http://127.0.0.1:5174",
        "http://localhost:3000", "http://127.0.0.1:3000"
    })
    @DisplayName("Origem de desenvolvimento passa no preflight e recebe o proprio Allow-Origin")
    void developmentOriginAccepted(String origin) throws Exception {
        mvc.perform(options("/api/v1/transactions/")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    @DisplayName("Origem fora da lista continua recusada com 403")
    void unknownOriginRejected() throws Exception {
        mvc.perform(options("/api/v1/transactions/")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}
