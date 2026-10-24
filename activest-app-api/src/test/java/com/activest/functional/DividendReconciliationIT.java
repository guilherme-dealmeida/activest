package com.activest.functional;

import com.activest.domain.dividend.DividendCalendar;
import com.activest.domain.integration.DividendReconciliation;
import com.activest.domain.integration.source.ImportedMovement;
import com.activest.domain.integration.source.MovementKind;
import com.activest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Conferencia dos proventos que a Area do Investidor da B3 informa pela API com os proventos da carteira
 * (docs/planos/PLANO_INTEGRACAO_B3.md, 6.8 e C3). So le — nada vai para o catalogo nem para a carteira:
 * cada provento ja existe, existe sem data de pagamento no catalogo, nao tem evento no catalogo, ou nao da
 * para conferir. Hoje fixo em 24/09/2026.
 */
class DividendReconciliationIT extends FunctionalBase {

    @MockBean private DividendCalendar dividendCalendar;

    @Autowired private DividendReconciliation reconciliation;

    private User owner;

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("conferencia@activest.test");
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
    }

    private static ImportedMovement dividendEvent(String ref, String ticker, String type, String payment, String shares,
                                             String net) {
        LocalDate day = payment == null ? null : LocalDate.parse(payment);
        return new ImportedMovement(ref, MovementKind.CASH_EVENT, ticker, null, new BigDecimal(shares), "BRL",
            new BigDecimal(net), null, null,
            (day != null ? day : LocalDate.of(2026, 1, 1)).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
            null, type, null, day);
    }

    private void event(String ticker, String type, String sourceName, String dataCom, String payment, String value) {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source, seq)
            SELECT id, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS NUMERIC), ?, 1
            FROM assets WHERE ticker = ?""", type, type, dataCom, payment, value, sourceName, ticker);
    }

    private Integer events(String ticker) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ?""",
            Integer.class, ticker);
    }

    @Test
    @DisplayName("Ja existe: o provento da carteira tem o mesmo pagamento, cotas e liquido")
    void alreadyExists() {
        event("HGLG11", "RENDIMENTO", "FNET", "2024-12-30", "2025-01-15", "1.10");
        recordSuccessfully(owner, buy("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, String> r = reconciliation.reconcile(owner.getId(),
            List.of(dividendEvent("L1", "HGLG11", "Rendimento", "2025-01-15", "10", "11.00")));

        assertThat(r.get("L1")).contains("já está na carteira");
        assertThat(events("HGLG11")).isEqualTo(1);
    }

    @Test
    @DisplayName("Sem data: o provento existe, mas o evento do catalogo nao tem pagamento — nada e gravado")
    void withoutDate() {
        event("BBAS3", "JCP", "B3_HISTORICO", "2024-12-11", null, "0.17649109403");
        recordSuccessfully(owner, buy("BBAS3", "2024-12-02", "19", "28.00"));

        Map<String, String> r = reconciliation.reconcile(owner.getId(),
            List.of(dividendEvent("L1", "BBAS3", "Juros Sobre Capital Próprio", "2024-12-27", "19", "2.85")));

        assertThat(r.get("L1")).contains("ainda não tem a data de pagamento");
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
            WHERE a.ticker = 'BBAS3' AND e.payment_date IS NOT NULL""", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sem evento: o catalogo nao tem o provento — a conferencia nao cria evento")
    void withoutEvent() {
        recordSuccessfully(owner, buy("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, String> r = reconciliation.reconcile(owner.getId(),
            List.of(dividendEvent("L1", "HGLG11", "Rendimento", "2025-01-15", "10", "11.00")));

        assertThat(r.get("L1")).contains("o catálogo não tem este evento");
        assertThat(events("HGLG11")).isZero();
    }

    @Test
    @DisplayName("Tres proventos iguais no mesmo dia (ISAE4) que ja existem: tres conferidos")
    void threeEqualAlreadyExisting() {
        event("ISAE4", "DIVIDENDO", "B3", "2026-03-12", "2026-04-29", "0.14131100000");
        event("ISAE4", "DIVIDENDO", "B3", "2026-04-02", "2026-04-29", "0.14131100000");
        event("ISAE4", "DIVIDENDO", "B3", "2026-04-17", "2026-04-29", "0.14131100000");
        recordSuccessfully(owner, buy("ISAE4", "2026-03-02", "20", "24.00"));

        Map<String, String> r = reconciliation.reconcile(owner.getId(), List.of(
            dividendEvent("L1", "ISAE4", "Dividendo", "2026-04-29", "20", "2.82"),
            dividendEvent("L2", "ISAE4", "Dividendo", "2026-04-29", "20", "2.82"),
            dividendEvent("L3", "ISAE4", "Dividendo", "2026-04-29", "20", "2.82")));

        assertThat(r.values()).hasSize(3).allSatisfy(m -> assertThat(m).contains("já está na carteira"));
        assertThat(events("ISAE4")).isEqualTo(3);
    }

    @Test
    @DisplayName("Nao conferido: ativo fora do catalogo e tipo que a B3 informa sem correspondencia")
    void notReconciled() {
        Map<String, String> r = reconciliation.reconcile(owner.getId(), List.of(
            dividendEvent("L1", "XXXX11", "Rendimento", "2025-01-15", "10", "11.00"),
            dividendEvent("L2", "HGLG11", "Transferência", "2025-01-15", "10", "11.00")));

        assertThat(r.get("L1")).contains("fora do catalogo");
        assertThat(r.get("L2")).contains("dados incompletos");
    }
}
