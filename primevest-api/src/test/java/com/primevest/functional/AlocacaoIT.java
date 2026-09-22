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
class AlocacaoIT extends FuncionalBase {

    private User dono;

    @BeforeEach
    void carteiraComReservaERendaVariavel() {
        dono = usuarioComCarteira("dono@primevest.test");

        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "60.00"));
        lancarComSucesso(dono, compra(CatalogIds.FII, "2026-09-01", "20", "100.00"));

        marcarComoReserva(dono, posicaoDe(dono, CatalogIds.CDB).get("id"), true);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> alocacao(boolean excluirReserva) {
        String path = "/api/v1/portfolio/allocation" + (excluirReserva ? "?excludeReservation=true" : "");
        return List.of(get(dono, path, Map[].class).getBody()).stream().map(m -> (Map<String, Object>) m).toList();
    }

    @Test
    @DisplayName("O Dashboard (sem excludeReservation) inclui a reserva")
    void dashboardIncluiReserva() {
        List<Map<String, Object>> alocacao = alocacao(false);

        assertThat(alocacao).extracting(l -> l.get("typeName"))
            .containsExactlyInAnyOrder("Ações", "FIIs", "Renda Fixa");
        BigDecimal total = alocacao.stream().map(l -> numero(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("A tela Alocacao (excludeReservation=true) deixa so a geracao de renda, somando 100%")
    void telaAlocacaoExcluiReserva() {
        List<Map<String, Object>> alocacao = alocacao(true);

        assertThat(alocacao).extracting(l -> l.get("typeName")).containsExactlyInAnyOrder("Ações", "FIIs");
        BigDecimal total = alocacao.stream().map(l -> numero(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("8000.00");

        BigDecimal somaDosPercentuais = alocacao.stream().map(l -> numero(l.get("percentage")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaDosPercentuais).isEqualByComparingTo("100.00");
        assertThat(numero(alocacao.stream().filter(l -> "Ações".equals(l.get("typeName")))
            .findFirst().orElseThrow().get("percentage"))).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("O total sem reserva e exatamente a geracao de renda do grafico da reserva — a amarracao da tela")
    void totalSemReservaBateComGeracaoDeRenda() {
        BigDecimal totalSemReserva = alocacao(true).stream().map(l -> numero(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        @SuppressWarnings("unchecked")
        Map<String, Object> reserva = get(dono, "/api/v1/portfolio/allocation/reservation", Map.class).getBody();

        assertThat(numero(reserva.get("incomeValue"))).isEqualByComparingTo(totalSemReserva);
        assertThat(numero(reserva.get("reservationValue"))).isEqualByComparingTo("2000.00");
        assertThat(numero(reserva.get("reservationPercentage"))).isEqualByComparingTo("20.00");
        assertThat(numero(reserva.get("incomePercentage"))).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("A alocacao por categoria nunca inclui a reserva e agrupa por tipo e categoria")
    void alocacaoDetalhada() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detalhada =
            List.of(get(dono, "/api/v1/portfolio/allocation/detailed", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();

        assertThat(detalhada).extracting(l -> l.get("typeName")).containsExactlyInAnyOrder("Ações", "FIIs");
        assertThat(detalhada).allSatisfy(tipo ->
            assertThat((List<?>) tipo.get("categories")).isNotEmpty());
    }

    @Test
    @DisplayName("Marcar um CDB como reserva muda a alocacao sem nenhuma outra escrita")
    void reservaMudaAAlocacao() {
        marcarComoReserva(dono, posicaoDe(dono, CatalogIds.CDB).get("id"), false);

        BigDecimal totalSemReserva = alocacao(true).stream().map(l -> numero(l.get("value")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalSemReserva).isEqualByComparingTo("10000.00");
        assertThat(alocacao(true)).extracting(l -> l.get("typeName"))
            .containsExactlyInAnyOrder("Ações", "FIIs", "Renda Fixa");
    }

    @Test
    @DisplayName("Posicao liquidada sai da alocacao")
    void posicaoLiquidadaSaiDaAlocacao() {
        lancarComSucesso(dono, venda(CatalogIds.FII, "2026-09-10", "20", "105.00"));

        assertThat(alocacao(false)).extracting(l -> l.get("typeName")).doesNotContain("FIIs");
    }
}
