package com.primevest.domain.portfolio;

import com.primevest.domain.portfolio.dto.PortfolioAllocationDTO;
import com.primevest.domain.portfolio.dto.PortfolioAssetReservationDTO;
import com.primevest.domain.portfolio.dto.PortfolioSummaryDTO;
import com.primevest.domain.portfolio.dto.ReservationAllocationDTO;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import com.primevest.support.MvcTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest extends MvcTestBase {

    @MockBean
    private PortfolioService portfolioService;

    @Autowired
    private PortfolioController controller;

    private static final UUID USUARIO = UUID.fromString(USER_ID);

    @Test
    @DisplayName("Sem token, todo endpoint da carteira responde 401 — e o 401 que leva o front ao login")
    void semTokenEQuatrocentosEUm() throws Exception {
        mvc.perform(get("/api/v1/portfolio/")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/portfolio/assets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/portfolio/allocation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/portfolio/allocation/detailed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/portfolio/allocation/reservation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/portfolio/performance")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("O resumo responde 200 com os campos que a tela le")
    void resumo() throws Exception {
        when(portfolioService.getPortfolioSummary(USUARIO)).thenReturn(new PortfolioSummaryDTO(
            UUID.randomUUID(), "Minha Carteira", new BigDecimal("1000.00"), new BigDecimal("1200.00"),
            new BigDecimal("50.00"), new BigDecimal("250.00"), new BigDecimal("25.00")));

        mvc.perform(get("/api/v1/portfolio/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInvested").value(1000.00))
            .andExpect(jsonPath("$.currentValue").value(1200.00))
            .andExpect(jsonPath("$.returnPercentage").value(25.00));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("O userId usado e sempre o do token, nunca um vindo da requisicao")
    void userIdVemDoToken() throws Exception {
        when(portfolioService.getPortfolioAssets(USUARIO)).thenReturn(List.of());

        mvc.perform(get("/api/v1/portfolio/assets")).andExpect(status().isOk());

        verify(portfolioService).getPortfolioAssets(USUARIO);
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("A alocacao sem parametro nao exclui a reserva (Dashboard); com excludeReservation=true, exclui")
    void parametroDeReserva() throws Exception {
        when(portfolioService.getAllocation(eq(USUARIO), any(Boolean.class)))
            .thenReturn(List.of(new PortfolioAllocationDTO("Ações", "#c9a84c",
                new BigDecimal("1000.00"), new BigDecimal("100.00"))));

        mvc.perform(get("/api/v1/portfolio/allocation")).andExpect(status().isOk());
        verify(portfolioService).getAllocation(USUARIO, false);

        mvc.perform(get("/api/v1/portfolio/allocation").param("excludeReservation", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].typeName").value("Ações"));
        verify(portfolioService).getAllocation(USUARIO, true);
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("A alocacao da reserva devolve os dois lados e os dois percentuais")
    void alocacaoDaReserva() throws Exception {
        when(portfolioService.getReservationAllocation(USUARIO)).thenReturn(new ReservationAllocationDTO(
            new BigDecimal("10000.00"), new BigDecimal("2000.00"), new BigDecimal("20.00"),
            new BigDecimal("8000.00"), new BigDecimal("80.00")));

        mvc.perform(get("/api/v1/portfolio/allocation/reservation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationPercentage").value(20.00))
            .andExpect(jsonPath("$.incomePercentage").value(80.00));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("O switch da reserva manda o estado desejado e responde a posicao atualizada")
    void ligarReserva() throws Exception {
        UUID posicao = UUID.randomUUID();
        when(portfolioService.setReservation(USUARIO, posicao, true))
            .thenReturn(new PortfolioAssetReservationDTO(posicao, "CDB", true));

        mvc.perform(patch("/api/v1/portfolio/assets/{id}/reservation", posicao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isReservation\": true}")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isReservation").value(true));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Corpo sem isReservation responde 400 com o campo apontado")
    void corpoSemEstado() throws Exception {
        mvc.perform(patch("/api/v1/portfolio/assets/{id}/reservation", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Dados inválidos"))
            .andExpect(jsonPath("$.errors.isReservation").exists());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Ativo que nao e renda fixa na reserva responde 422 com a mensagem do dominio")
    void reservaEmAtivoQueNaoERendaFixa() throws Exception {
        UUID posicao = UUID.randomUUID();
        when(portfolioService.setReservation(USUARIO, posicao, true))
            .thenThrow(new BusinessException("Somente ativos de renda fixa podem compor a reserva de emergência"));

        mvc.perform(patch("/api/v1/portfolio/assets/{id}/reservation", posicao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isReservation\": true}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Somente ativos de renda fixa podem compor a reserva de emergência"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("Posicao de outro dono responde 404, nunca 403")
    void posicaoDeOutroDono() throws Exception {
        UUID posicao = UUID.randomUUID();
        when(portfolioService.setReservation(USUARIO, posicao, true))
            .thenThrow(new ResourceNotFoundException("Posição não encontrada"));

        mvc.perform(patch("/api/v1/portfolio/assets/{id}/reservation", posicao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isReservation\": true}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Posição não encontrada"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F9 — caminho sem rota responde 404, e nao 500")
    void rotaInexistente() throws Exception {
        mvc.perform(get("/api/v1/portfolio/nao-existe"))
            .andExpect(status().isNotFound());
    }
}
