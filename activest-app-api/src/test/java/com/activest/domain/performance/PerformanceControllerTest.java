package com.activest.domain.performance;

import com.activest.support.MvcTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** docs/planos/PLANO_RENTABILIDADE.md, secao 5: autenticacao, parametros e o JSON que a tela le. */
@WebMvcTest(PerformanceController.class)
class PerformanceControllerTest extends MvcTestBase {

    @MockBean
    private PerformanceService service;

    private static final UUID USER = UUID.fromString(USER_ID);

    @Test
    @DisplayName("Sem token, todo endpoint da Rentabilidade responde 401")
    void withoutToken() throws Exception {
        for (String p : List.of("benchmarks", "summary", "series", "periodic", "table", "breakdown", "wealth", "risk")) {
            mvc.perform(get("/api/v1/performance/" + p)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Parametros: recorte, base, reserva e benchmarks (IPCA + x com a taxa)")
    void parameters() throws Exception {
        PerformanceDTOs.CoverageDTO cov = new PerformanceDTOs.CoverageDTO(null, null, null, null, true, List.of(),
                List.of(), null, true, "GROSS", "WITHOUT", false);
        when(service.summary(eq(USER), any())).thenReturn(new PerformanceDTOs.SummaryDTO(cov, 12.5, null, null,
                null, null, null, null, null, null, null, null, BigDecimal.TEN, null, null, null, null, List.of()));

        mvc.perform(get("/api/v1/performance/summary?range=1A&type=acao&basis=gross&reservation=without"
                        + "&benchmarks=CDI,IPCA_MAIS:6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twr").value(12.5))
                .andExpect(jsonPath("$.coverage.basisApplies").value(true));

        ArgumentCaptor<PerformanceService.Request> req = ArgumentCaptor.forClass(PerformanceService.Request.class);
        verify(service).summary(eq(USER), req.capture());
        assertThat(req.getValue().range()).isEqualTo("1A");
        assertThat(req.getValue().scope().typeName()).isEqualTo("ACAO");
        assertThat(req.getValue().scope().basis()).isEqualTo(PerformanceBasis.GROSS);
        assertThat(req.getValue().scope().reservation()).isEqualTo(ReservationFilter.WITHOUT);
        assertThat(req.getValue().benchmarks()).containsExactly(new BenchmarkData.Ref("CDI", null),
                new BenchmarkData.Ref("IPCA_MAIS", 6.0));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Base ou taxa invalida: 422 com a mensagem")
    void invalid() throws Exception {
        mvc.perform(get("/api/v1/performance/summary?basis=xyz")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/performance/summary?benchmarks=IPCA_MAIS:abc"))
                .andExpect(status().isUnprocessableEntity());
    }
}
