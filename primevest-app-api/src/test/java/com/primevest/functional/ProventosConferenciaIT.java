package com.primevest.functional;

import com.primevest.domain.dividend.DividendCalendar;
import com.primevest.domain.integration.ProventosConferencia;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.user.User;
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
class ProventosConferenciaIT extends FuncionalBase {

    @MockBean private DividendCalendar dividendCalendar;

    @Autowired private ProventosConferencia conferencia;

    private User dono;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("conferencia@primevest.test");
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
    }

    private static ImportedMovement provento(String ref, String ticker, String tipo, String pagamento, String cotas,
                                             String liquido) {
        LocalDate dia = pagamento == null ? null : LocalDate.parse(pagamento);
        return new ImportedMovement(ref, MovementKind.CASH_EVENT, ticker, null, new BigDecimal(cotas), "BRL",
            new BigDecimal(liquido), null, null,
            (dia != null ? dia : LocalDate.of(2026, 1, 1)).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
            null, tipo, null, dia);
    }

    private void evento(String ticker, String tipo, String fonte, String dataCom, String pagamento, String valor) {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source, seq)
            SELECT id, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS NUMERIC), ?, 1
            FROM assets WHERE ticker = ?""", tipo, tipo, dataCom, pagamento, valor, fonte, ticker);
    }

    private Integer eventos(String ticker) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ?""",
            Integer.class, ticker);
    }

    @Test
    @DisplayName("Ja existe: o provento da carteira tem o mesmo pagamento, cotas e liquido")
    void jaExiste() {
        evento("HGLG11", "RENDIMENTO", "FNET", "2024-12-30", "2025-01-15", "1.10");
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, String> r = conferencia.conferir(dono.getId(),
            List.of(provento("L1", "HGLG11", "Rendimento", "2025-01-15", "10", "11.00")));

        assertThat(r.get("L1")).contains("já está na carteira");
        assertThat(eventos("HGLG11")).isEqualTo(1);
    }

    @Test
    @DisplayName("Sem data: o provento existe, mas o evento do catalogo nao tem pagamento — nada e gravado")
    void semData() {
        evento("BBAS3", "JCP", "B3_HISTORICO", "2024-12-11", null, "0.17649109403");
        lancarComSucesso(dono, compra("BBAS3", "2024-12-02", "19", "28.00"));

        Map<String, String> r = conferencia.conferir(dono.getId(),
            List.of(provento("L1", "BBAS3", "Juros Sobre Capital Próprio", "2024-12-27", "19", "2.85")));

        assertThat(r.get("L1")).contains("ainda não tem a data de pagamento");
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
            WHERE a.ticker = 'BBAS3' AND e.payment_date IS NOT NULL""", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sem evento: o catalogo nao tem o provento — a conferencia nao cria evento")
    void semEvento() {
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, String> r = conferencia.conferir(dono.getId(),
            List.of(provento("L1", "HGLG11", "Rendimento", "2025-01-15", "10", "11.00")));

        assertThat(r.get("L1")).contains("o catálogo não tem este evento");
        assertThat(eventos("HGLG11")).isZero();
    }

    @Test
    @DisplayName("Tres proventos iguais no mesmo dia (ISAE4) que ja existem: tres conferidos")
    void tresIguaisJaExistentes() {
        evento("ISAE4", "DIVIDENDO", "B3", "2026-03-12", "2026-04-29", "0.14131100000");
        evento("ISAE4", "DIVIDENDO", "B3", "2026-04-02", "2026-04-29", "0.14131100000");
        evento("ISAE4", "DIVIDENDO", "B3", "2026-04-17", "2026-04-29", "0.14131100000");
        lancarComSucesso(dono, compra("ISAE4", "2026-03-02", "20", "24.00"));

        Map<String, String> r = conferencia.conferir(dono.getId(), List.of(
            provento("L1", "ISAE4", "Dividendo", "2026-04-29", "20", "2.82"),
            provento("L2", "ISAE4", "Dividendo", "2026-04-29", "20", "2.82"),
            provento("L3", "ISAE4", "Dividendo", "2026-04-29", "20", "2.82")));

        assertThat(r.values()).hasSize(3).allSatisfy(m -> assertThat(m).contains("já está na carteira"));
        assertThat(eventos("ISAE4")).isEqualTo(3);
    }

    @Test
    @DisplayName("Nao conferido: ativo fora do catalogo e tipo que a B3 informa sem correspondencia")
    void naoConferido() {
        Map<String, String> r = conferencia.conferir(dono.getId(), List.of(
            provento("L1", "XXXX11", "Rendimento", "2025-01-15", "10", "11.00"),
            provento("L2", "HGLG11", "Transferência", "2025-01-15", "10", "11.00")));

        assertThat(r.get("L1")).contains("fora do catalogo");
        assertThat(r.get("L2")).contains("dados incompletos");
    }
}
