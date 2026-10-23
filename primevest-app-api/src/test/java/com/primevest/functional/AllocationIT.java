package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tela Alocacao e o Dashboard — que leem o mesmo endpoint com criterios
 * diferentes sobre a reserva de emergencia.
 */
class AllocationIT extends FunctionalBase {

    private User owner;

    @BeforeEach
    void portfolioWithReserveAndEquities() {
        owner = userWithPortfolio("dono@primevest.test");

        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "60.00"));
        recordSuccessfully(owner, buy(CatalogIds.FII, "2026-09-01", "20", "100.00"));

        markAsReserve(owner, positionOf(owner, CatalogIds.CDB).get("id"), true);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> allocation(boolean removeReserve) {
        String path = "/api/v1/portfolio/allocation" + (removeReserve ? "?excludeReservation=true" : "");
        return List.of(get(owner, path, Map[].class).getBody()).stream().map(m -> (Map<String, Object>) m).toList();
    }

    @Test
    @DisplayName("O Dashboard (sem excludeReservation) inclui a reserva")
    void dashboardIncludesReserve() {
        List<Map<String, Object>> allocation = allocation(false);

        assertThat(allocation).extracting(l -> l.get("typeName"))
            .containsExactlyInAnyOrder("Ações", "FIIs", "Renda Fixa");
        BigDecimal total = allocation.stream().map(l -> number(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("A tela Alocacao (excludeReservation=true) deixa so a geracao de renda, somando 100%")
    void allocationScreenExcludesReserve() {
        List<Map<String, Object>> allocation = allocation(true);

        assertThat(allocation).extracting(l -> l.get("typeName")).containsExactlyInAnyOrder("Ações", "FIIs");
        BigDecimal total = allocation.stream().map(l -> number(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("8000.00");

        BigDecimal sumOfPercentages = allocation.stream().map(l -> number(l.get("percentage")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfPercentages).isEqualByComparingTo("100.00");
        assertThat(number(allocation.stream().filter(l -> "Ações".equals(l.get("typeName")))
            .findFirst().orElseThrow().get("percentage"))).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("O total sem reserva e exatamente a geracao de renda do grafico da reserva — a amarracao da tela")
    void totalWithoutReserveMatchesIncomeGeneration() {
        BigDecimal totalWithoutReserve = allocation(true).stream().map(l -> number(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        @SuppressWarnings("unchecked")
        Map<String, Object> reserve = get(owner, "/api/v1/portfolio/allocation/reservation", Map.class).getBody();

        assertThat(number(reserve.get("incomeValue"))).isEqualByComparingTo(totalWithoutReserve);
        assertThat(number(reserve.get("reservationValue"))).isEqualByComparingTo("2000.00");
        assertThat(number(reserve.get("reservationPercentage"))).isEqualByComparingTo("20.00");
        assertThat(number(reserve.get("incomePercentage"))).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("A alocacao por categoria nunca inclui a reserva e agrupa por tipo e categoria")
    void detailedAllocation() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailed =
            List.of(get(owner, "/api/v1/portfolio/allocation/detailed", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();

        assertThat(detailed).extracting(l -> l.get("typeName")).containsExactlyInAnyOrder("Ações", "FIIs");
        assertThat(detailed).allSatisfy(type ->
            assertThat((List<?>) type.get("categories")).isNotEmpty());
    }

    @Test
    @DisplayName("Marcar um CDB como reserva muda a alocacao sem nenhuma outra escrita")
    void reserveChangesAllocation() {
        markAsReserve(owner, positionOf(owner, CatalogIds.CDB).get("id"), false);

        BigDecimal totalWithoutReserve = allocation(true).stream().map(l -> number(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalWithoutReserve).isEqualByComparingTo("10000.00");
        assertThat(allocation(true)).extracting(l -> l.get("typeName"))
            .containsExactlyInAnyOrder("Ações", "FIIs", "Renda Fixa");
    }

    @Test
    @DisplayName("Posicao liquidada sai da alocacao")
    void settledPositionLeavesAllocation() {
        recordSuccessfully(owner, sale(CatalogIds.FII, "2026-09-10", "20", "105.00"));

        assertThat(allocation(false)).extracting(l -> l.get("typeName")).doesNotContain("FIIs");
    }
}
