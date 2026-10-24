package com.activest.adm.domain.marketindex;

import com.activest.adm.integration.indices.B3IndexClient;
import com.activest.adm.integration.indices.BcbSgsClient;
import com.activest.adm.integration.indices.IndexFixtures;
import com.activest.adm.integration.indices.IndexSourceException;
import com.activest.domain.marketindex.IndexPoint;
import com.activest.domain.user.User;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * X3 do docs/planos/PLANO_INDICES_MERCADO.md: carga dos indices com as respostas reais de 2024 (CDI, IPCA,
 * Ibovespa). Fontes mockadas no nivel do cliente; o SQL e o banco sao de verdade.
 */
class MarketIndexImportIT extends IntegrationTestBase {

    @MockBean
    BcbSgsClient sgs;

    @MockBean
    B3IndexClient b3;

    @Autowired
    MarketIndexImportService service;

    @BeforeEach
    void sources() throws Exception {
        when(sgs.year(anyString(), anyInt())).thenReturn(List.of());
        when(b3.year(anyString(), anyInt())).thenReturn(List.of());
        when(sgs.year("12", 2024)).thenReturn(parseSgs("sgs_12_2024"));
        when(sgs.year("433", 2024)).thenReturn(parseSgs("sgs_433_2024"));
        when(b3.year("IBOV", 2024)).thenReturn(parseB3("b3_IBOV_2024", 2024));
    }

    @SuppressWarnings("unchecked")
    private static List<IndexPoint> parseSgs(String fixture) throws Exception {
        Method m = BcbSgsClient.class.getDeclaredMethod("parse", String.class);
        m.setAccessible(true);
        return (List<IndexPoint>) m.invoke(null, IndexFixtures.body(fixture));
    }

    @SuppressWarnings("unchecked")
    private static List<IndexPoint> parseB3(String fixture, int year) throws Exception {
        Method m = B3IndexClient.class.getDeclaredMethod("parse", String.class, int.class);
        m.setAccessible(true);
        return (List<IndexPoint>) m.invoke(null, IndexFixtures.body(fixture), year);
    }

    private int count(String code) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_index_values v JOIN market_indices i "
                + "ON i.id = v.index_id WHERE i.code = ?", Integer.class, code);
    }

    private BigDecimal value(String code, LocalDate d) {
        return jdbc.queryForObject("SELECT v.value FROM market_index_values v JOIN market_indices i "
                + "ON i.id = v.index_id WHERE i.code = ? AND v.reference_date = ?", BigDecimal.class, code,
                Date.valueOf(d));
    }

    @Test
    @DisplayName("Carga de 2024: CDI 253 dias, IPCA 12 meses, Ibovespa 251 pregoes; a segunda rodada nao muda nada")
    void idempotentLoad() {
        MarketIndexImportService.Result r = service.importYears(List.of("CDI", "IPCA", "IBOV"), 2024, 2024, "teste");

        assertThat(r.created()).isEqualTo(253 + 12 + 251);
        assertThat(r.failures()).isEmpty();
        assertThat(count("CDI")).isEqualTo(253);
        assertThat(count("IPCA")).isEqualTo(12);
        assertThat(count("IBOV")).isEqualTo(251);
        assertThat(value("CDI", LocalDate.of(2024, 1, 2))).isEqualByComparingTo("0.043739");
        assertThat(value("IBOV", LocalDate.of(2024, 12, 30))).isEqualByComparingTo("120283.40");
        assertThat(r.changedFrom()).containsEntry("CDI", LocalDate.of(2024, 1, 2))
                .containsEntry("IPCA", LocalDate.of(2024, 1, 1));

        MarketIndexImportService.Result de = service.importYears(List.of("cdi", "IPCA", "IBOV"), 2024, 2024, "teste");
        assertThat(de.created()).isZero();
        assertThat(de.updated()).isZero();
        assertThat(de.changedFrom()).isEmpty();
        assertThat(de.indices()).extracting(MarketIndexImportService.IndexResult::unchanged)
                .containsExactly(253, 12, 251);
    }

    @Test
    @DisplayName("Valor corrigido pela fonte e sobrescrito (D10) e a data entra em changedFrom")
    void correction() {
        service.importYears(List.of("IPCA"), 2024, 2024, "teste");
        jdbc.update("UPDATE market_index_values SET value = 9.99 WHERE reference_date = ? AND index_id = "
                + "(SELECT id FROM market_indices WHERE code = 'IPCA')", Date.valueOf(LocalDate.of(2024, 8, 1)));

        MarketIndexImportService.Result r = service.importYears(List.of("IPCA"), 2024, 2024, "teste");

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.changedFrom()).containsExactly(Map.entry("IPCA", LocalDate.of(2024, 8, 1)));
        assertThat(value("IPCA", LocalDate.of(2024, 8, 1))).isEqualByComparingTo("-0.02");
    }

    @Test
    @DisplayName("Indice que falha nao impede os outros")
    void isolatedFailure() {
        when(sgs.year("433", 2024)).thenThrow(new IndexSourceException("SGS fora"));

        MarketIndexImportService.Result r = service.importYears(List.of("CDI", "IPCA"), 2024, 2024, "teste");

        assertThat(r.failures()).containsExactly("IPCA");
        assertThat(count("CDI")).isEqualTo(253);
        assertThat(count("IPCA")).isZero();
    }

    @Test
    @DisplayName("Incremental: indice com serie relê so a janela de revisao; indice vazio faz o historico desde 2010")
    void incremental() {
        service.importYears(List.of("CDI"), 2024, 2024, "teste");

        service.importRecent("teste");

        int year = LocalDate.now().getYear();
        verify(sgs, never()).year("12", 2023);
        verify(sgs, org.mockito.Mockito.times(2)).year("12", 2024);
        verify(sgs).year("12", year);
        verify(sgs).year(eq("433"), eq(2010));
        verify(b3).year("IFIX", 2010);
    }

    @Test
    @DisplayName("Data futura nao entra: a meta Selic vem preenchida ate a proxima reuniao do Copom")
    void withoutFutureDate() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        when(sgs.year("432", today.getYear())).thenReturn(List.of(
                new IndexPoint(today.minusDays(1), new BigDecimal("15.00")),
                new IndexPoint(today.plusDays(1), new BigDecimal("15.00"))));

        service.importYears(List.of("SELIC_META"), today.getYear(), today.getYear(), "teste");

        assertThat(count("SELIC_META")).isEqualTo(1);
    }

    @Test
    @DisplayName("Conferencia: indice vazio ou parado em 2024 esta atrasado")
    void lateOnes() {
        service.importYears(List.of("CDI"), 2024, 2024, "teste");

        List<MarketIndexImportService.Stale> late = service.stale();

        assertThat(late).hasSize(10);
        assertThat(late).contains(new MarketIndexImportService.Stale("CDI", LocalDate.of(2024, 12, 31)),
                new MarketIndexImportService.Stale("IPCA", null));
    }

    @Test
    @DisplayName("Conferencia vira mensagem de administrador por indice atrasado; o que volta ao dia fica resolvido")
    void reconciliationBecomesMessage() {
        service.importYears(List.of("CDI"), 2024, 2024, "teste");

        service.audit("teste");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'CATALOGO_INDICE_ATRASADO' "
                + "AND audience = 'ADMIN' AND status = 'NAO_RESOLVIDA'", Integer.class)).isEqualTo(10);

        // de novo: nao repete
        service.audit("teste");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'CATALOGO_INDICE_ATRASADO'",
                Integer.class)).isEqualTo(10);

        // o CDI volta ao dia: a mensagem dele fica resolvida
        jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) SELECT id, CURRENT_DATE, 0.05 "
                + "FROM market_indices WHERE code = 'CDI'");
        service.audit("teste");
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE kind = 'CATALOGO_INDICE_ATRASADO' "
                + "AND dedup_key = 'CATALOGO_INDICE_ATRASADO:CDI'", String.class)).isEqualTo("RESOLVIDA");
    }

    @Test
    @DisplayName("Codigo desconhecido e recusado antes de qualquer carga; o endpoint responde 400")
    void unknownCode() throws InterruptedException {
        assertThatThrownBy(() -> service.importYears(List.of("CDI", "XPTO"), 2024, 2024, "teste"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("XPTO");

        User admin = userWithPortfolio("admin-indices@activest.test");
        jdbc.update("UPDATE users SET is_admin = TRUE WHERE id = ?", admin.getId());
        assertThat(post(admin, "/api/v1/admin/indices?codes=XPTO", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(admin, "/api/v1/admin/indices?codes=CDI&from=2024&to=2024", null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // a carga roda em segundo plano: espera terminar para nao invadir o cenario seguinte
        long ate = System.currentTimeMillis() + 10_000;
        while (count("CDI") < 253 && System.currentTimeMillis() < ate) {
            Thread.sleep(100);
        }
        assertThat(count("CDI")).isEqualTo(253);
    }
}
