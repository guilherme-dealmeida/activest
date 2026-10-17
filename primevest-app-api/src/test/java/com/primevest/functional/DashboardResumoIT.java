package com.primevest.functional;

import com.primevest.domain.calendar.BusinessCalendar;
import com.primevest.domain.dividend.DividendCalendar;
import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Cards do Dashboard que saem do resumo da carteira ({@code GET /portfolio/}, escopo "Mudancas de tela", 17/10/2026):
 * Aportes (compras - o que as vendas devolveram, nos ultimos 12 meses) e o Valor liquido do Patrimonio (liquido
 * estimado na renda fixa e no Tesouro; nos demais tipos, o proprio valor atual). Os proventos de 12 meses estao em
 * {@link ProventosAReceberIT}.
 *
 * <p>Hoje fixo em 24/09/2026: a janela dos 12 meses vai de 24/09/2025 (exclusive) a 24/09/2026.</p>
 */
class DashboardResumoIT extends FuncionalBase {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 24);
    private static final String BBAS3 = "BBAS3";

    @MockBean private DividendCalendar dividendCalendar;

    private User dono;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("dashboard@primevest.test");
        when(dividendCalendar.today()).thenReturn(HOJE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resumo() {
        return get(dono, "/api/v1/portfolio/", Map.class).getBody();
    }

    /** CDI de 0,05% a.d. em cada dia util de {@code de} a {@code ate}, como no RendaFixaCurvaIT. */
    private void cdi(LocalDate de, LocalDate ate) {
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            if (BusinessCalendar.isBusinessDay(d)) {
                jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) "
                        + "SELECT id, ?, 0.05 FROM market_indices WHERE code = 'CDI' ON CONFLICT DO NOTHING", Date.valueOf(d));
            }
        }
    }

    @Test
    @DisplayName("Aportes: compras - vendas dos ultimos 12 meses; o que cai fora da janela (inclusive o dia de corte) nao entra")
    void aportesLiquidosDozeMeses() {
        lancarComSucesso(dono, compra(BBAS3, "2025-06-10", "100", "20.00"));   // fora da janela
        lancarComSucesso(dono, compra(BBAS3, "2025-09-24", "10", "20.00"));    // dia de corte: fora
        lancarComSucesso(dono, compra(BBAS3, "2026-03-10", "50", "22.00"));    // 1.100,00
        lancarComSucesso(dono, venda(BBAS3, "2026-05-11", "30", "25.00"));     // devolveu 750,00

        Map<String, Object> r = resumo();
        assertThat(numero(r.get("contributions12m"))).isEqualByComparingTo("350.00");
        assertThat(numero(r.get("contributionsMonthlyAverage12m"))).isEqualByComparingTo("29.17");
    }

    @Test
    @DisplayName("Aportes negativos quando se resgatou mais do que se aplicou na janela")
    void aportesNegativos() {
        lancarComSucesso(dono, compra(BBAS3, "2025-06-10", "100", "20.00"));
        lancarComSucesso(dono, venda(BBAS3, "2026-02-02", "60", "25.00"));

        assertThat(numero(resumo().get("contributions12m"))).isEqualByComparingTo("-1500.00");
    }

    @Test
    @DisplayName("Valor liquido: sem renda fixa nem Tesouro, e o proprio valor atual")
    void liquidoSoRendaVariavel() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-03-10", "50", "38.00"));
        lancarComSucesso(dono, compra(BBAS3, "2026-03-10", "100", "22.00"));

        Map<String, Object> r = resumo();
        assertThat(numero(r.get("currentNetValue"))).isEqualByComparingTo(numero(r.get("currentValue")));
    }

    @Test
    @DisplayName("Valor liquido: a renda fixa entra pelo liquido estimado da Carteira; o resto pelo valor atual")
    void liquidoComRendaFixa() {
        cdi(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));
        Map<String, Object> cdb = aplicacaoEmCdb(CatalogIds.CDB, "2024-01-02", "1000.00", "BANCO TESTE");
        cdb.put("indexerRate", new BigDecimal("100"));
        lancarComSucesso(dono, cdb);
        lancarComSucesso(dono, compra(BBAS3, "2026-03-10", "100", "22.00"));

        Map<String, Object> linhaCdb = posicaoDe(dono, CatalogIds.CDB);
        BigDecimal liquidoCdb = numero(linhaCdb.get("estimatedNet"));
        assertThat(liquidoCdb).as("IR e IOF sobre o rendimento").isLessThan(numero(linhaCdb.get("currentValue")));

        BigDecimal esperado = liquidoCdb.add(numero(posicaoDe(dono, BBAS3).get("currentValue")));
        Map<String, Object> r = resumo();
        assertThat(numero(r.get("currentNetValue"))).isEqualByComparingTo(esperado);
        assertThat(numero(r.get("currentNetValue"))).isLessThan(numero(r.get("currentValue")));
    }
}
