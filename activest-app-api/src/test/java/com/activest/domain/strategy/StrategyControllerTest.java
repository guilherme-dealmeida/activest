package com.activest.domain.strategy;

import com.activest.domain.strategy.dto.RebalanceDTO;
import com.activest.domain.strategy.dto.StrategyDTO;
import com.activest.domain.strategy.dto.StrategyReservationDTO;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import com.activest.support.MvcTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
class StrategyControllerTest extends MvcTestBase {

    @MockBean private StrategyService strategyService;
    @MockBean private StrategyReservationService strategyReservationService;

    private static final UUID USER = UUID.fromString(USER_ID);

    private static final String VALID_STRATEGY = """
        {"items":[{"assetTypeId":1,"targetPercentage":60.00},{"assetTypeId":2,"targetPercentage":40.00}]}
        """;

    @Test
    @DisplayName("Sem token, todo endpoint de estrategia responde 401")
    void withoutToken() throws Exception {
        mvc.perform(get("/api/v1/strategy/")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/strategy/rebalance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/strategy/reservation")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/strategy/").contentType(MediaType.APPLICATION_JSON).content(VALID_STRATEGY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("A estrategia responde 200 com tipo e percentual de cada item")
    void list() throws Exception {
        when(strategyService.getUserStrategy(USER)).thenReturn(List.of(
            new StrategyDTO(UUID.randomUUID(), 1L, "Ações", new BigDecimal("60.00"))));

        mvc.perform(get("/api/v1/strategy/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].assetTypeName").value("Ações"))
            .andExpect(jsonPath("$[0].targetPercentage").value(60.00));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F2 — percentual acima de 100 em um item responde 400 (o @Valid desce na lista)")
    void itemAboveHundred() throws Exception {
        mvc.perform(put("/api/v1/strategy/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"assetTypeId\":1,\"targetPercentage\":150.00}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Dados inválidos"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F2 — item sem tipo de ativo ou sem percentual responde 400")
    void incompleteItem() throws Exception {
        mvc.perform(put("/api/v1/strategy/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"targetPercentage\":100.00}]}"))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/strategy/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"assetTypeId\":1}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F7 — soma diferente de 100% responde 422 com o total atual na mensagem")
    void sumDifferentFromHundred() throws Exception {
        when(strategyService.updateStrategy(eq(USER), any()))
            .thenThrow(new BusinessException("A soma dos percentuais ideais deve ser 100%. Atual: 90%"));

        mvc.perform(put("/api/v1/strategy/").contentType(MediaType.APPLICATION_JSON).content(VALID_STRATEGY))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("A soma dos percentuais ideais deve ser 100%. Atual: 90%"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F6 — tipo de ativo inexistente responde 404")
    void missingType() throws Exception {
        when(strategyService.updateStrategy(eq(USER), any()))
            .thenThrow(new ResourceNotFoundException("Tipo de ativo não encontrado: 99"));

        mvc.perform(put("/api/v1/strategy/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"assetTypeId\":99,\"targetPercentage\":100.00}]}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Tipo de ativo não encontrado: 99"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("F8 — o DELETE da estrategia nao existe mais: responde 404")
    void deleteNoLongerExists() throws Exception {
        mvc.perform(delete("/api/v1/strategy/{assetTypeId}", 1))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("A reserva trafega pelo nome do tipo (PERCENTAGE / VALUE)")
    void reserveByTypeName() throws Exception {
        when(strategyReservationService.get(USER)).thenReturn(new StrategyReservationDTO(
            UUID.randomUUID(), ReservationType.PERCENTAGE, new BigDecimal("30.00"), null,
            new BigDecimal("10000.00"), new BigDecimal("2000.00"), new BigDecimal("8000.00"),
            new BigDecimal("20.00"), new BigDecimal("30.00"), new BigDecimal("-10.00")));

        mvc.perform(get("/api/v1/strategy/reservation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.typeReservation").value("PERCENTAGE"))
            .andExpect(jsonPath("$.diffPercentage").value(-10.00));
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("A reserva sem tipo, ou com percentual negativo, responde 400")
    void invalidReserve() throws Exception {
        mvc.perform(put("/api/v1/strategy/reservation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetPercentage\":30.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.typeReservation").exists());

        mvc.perform(put("/api/v1/strategy/reservation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeReservation\":\"PERCENTAGE\",\"targetPercentage\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.targetPercentage").exists());
    }

    @Test
    @WithMockUser(username = USER_ID)
    @DisplayName("O rebalanceamento devolve os itens por tipo e a linha da reserva separada")
    void rebalance() throws Exception {
        when(strategyService.getRebalanceSuggestion(USER)).thenReturn(new RebalanceDTO(
            new BigDecimal("8000.00"),
            List.of(new RebalanceDTO.RebalanceItem("Ações", new BigDecimal("50.00"), new BigDecimal("75.00"),
                new BigDecimal("25.00"), new BigDecimal("6000.00"), new BigDecimal("6000.00"), BigDecimal.ZERO)),
            new RebalanceDTO.ReservationRebalance(ReservationType.PERCENTAGE, new BigDecimal("30.00"), null,
                new BigDecimal("2000.00"), new BigDecimal("1428.57"), new BigDecimal("3428.57"),
                new BigDecimal("20.00"), new BigDecimal("-10.00"))));

        mvc.perform(get("/api/v1/strategy/rebalance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].typeName").value("Ações"))
            .andExpect(jsonPath("$.reservation.amountToRebalance").value(1428.57));
    }
}
