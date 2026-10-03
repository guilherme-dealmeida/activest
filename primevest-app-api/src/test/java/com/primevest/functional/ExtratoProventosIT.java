package com.primevest.functional;

import com.primevest.domain.dividend.DividendCalendar;
import com.primevest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Conferencia do extrato de proventos da B3: POST /admin/proventos/extrato. So le — nada vai
 * para o catalogo nem para a carteira (docs/planos/PLANO_CARGA_INICIAL.md, D13): cada linha ja existe, existe
 * sem data de pagamento no catalogo, nao tem evento no catalogo, ou e recusada.
 *
 * <p>Hoje fixo em 24/09/2026 (recebido x a receber).</p>
 */
class ExtratoProventosIT extends FuncionalBase {

    private static final String URL = "/api/v1/admin/proventos/extrato";

    @MockBean private DividendCalendar dividendCalendar;

    private User dono;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("extrato@primevest.test");
        jdbc.update("UPDATE users SET is_admin = TRUE WHERE id = ?", dono.getId());
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
    }

    // ------------------------------------------------------------------
    // Atalhos
    // ------------------------------------------------------------------

    private static Map<String, Object> linha(String ref, String ticker, String tipo, String pagamento, String cotas,
                                             String liquido, String bruto, String dataCom) {
        Map<String, Object> l = new HashMap<>();
        l.put("ref", ref);
        l.put("ticker", ticker);
        l.put("type", tipo);
        l.put("paymentDate", pagamento);
        l.put("shares", new BigDecimal(cotas));
        l.put("netAmount", new BigDecimal(liquido));
        l.put("grossAmount", bruto == null ? null : new BigDecimal(bruto));
        l.put("dataCom", dataCom);
        return l;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> importar(List<Map<String, Object>> linhas) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("lines", linhas);
        ResponseEntity<Map> r = post(dono, URL, corpo, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<String> situacoes(Map<String, Object> resultado) {
        List<String> s = new ArrayList<>();
        for (Map<String, Object> i : (List<Map<String, Object>>) resultado.get("items")) {
            s.add((String) i.get("status"));
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static String mensagem(Map<String, Object> resultado, int i) {
        return (String) ((List<Map<String, Object>>) resultado.get("items")).get(i).get("message");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> proventos() {
        return (List<Map<String, Object>>) get(dono, "/api/v1/dividends/", Map.class).getBody().get("recent");
    }

    private void evento(String ticker, String tipo, String fonte, String dataCom, String pagamento, String valor, int seq) {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source, seq)
            SELECT id, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS NUMERIC), ?, ?
            FROM assets WHERE ticker = ?""", tipo, tipo, dataCom, pagamento, valor, fonte, seq, ticker);
    }

    private Integer eventos(String ticker) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ?""",
            Integer.class, ticker);
    }

    // ------------------------------------------------------------------
    // Cenarios
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Ja existe: o provento da carteira tem o mesmo pagamento, cotas e liquido")
    void jaExiste() {
        evento("HGLG11", "RENDIMENTO", "FNET", "2024-12-30", "2025-01-15", "1.10", 1);
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, Object> r = importar(
            List.of(linha("L1", "HGLG11", "RENDIMENTO", "2025-01-15", "10", "11.00", "11.00", "2024-12-30")));

        assertThat(situacoes(r)).containsExactly("JA_EXISTE");
        assertThat(eventos("HGLG11")).isEqualTo(1);
    }

    @Test
    @DisplayName("Sem data: o provento existe, mas o evento do catalogo nao tem pagamento — nada e gravado")
    void semData() {
        evento("BBAS3", "JCP", "B3_HISTORICO", "2024-12-11", null, "0.17649109403", 1);
        lancarComSucesso(dono, compra("BBAS3", "2024-12-02", "19", "28.00"));

        Map<String, Object> r = importar(List.of(linha("L1", "BBAS3", "JCP", "2024-12-27", "19", "2.85", null, null)));

        assertThat(situacoes(r)).containsExactly("SEM_DATA");
        assertThat(proventos()).singleElement().satisfies(p -> assertThat(p.get("paymentDate")).isNull());
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
            WHERE a.ticker = 'BBAS3' AND e.payment_date IS NOT NULL""", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sem evento: nada no PrimeVest — o extrato nao cria evento no catalogo")
    void semEvento() {
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, Object> r = importar(
            List.of(linha("L1", "HGLG11", "RENDIMENTO", "2025-01-15", "10", "11.00", "11.00", "2024-12-30")));

        assertThat(situacoes(r)).containsExactly("SEM_EVENTO");
        assertThat(eventos("HGLG11")).isZero();
        assertThat(proventos()).isEmpty();
    }

    @Test
    @DisplayName("Tres linhas iguais no mesmo dia (ISAE4) que ja existem: tres JA_EXISTE")
    void tresLinhasIguaisJaExistentes() {
        evento("ISAE4", "DIVIDENDO", "B3", "2026-03-12", "2026-04-29", "0.14131100000", 1);
        evento("ISAE4", "DIVIDENDO", "B3", "2026-04-02", "2026-04-29", "0.14131100000", 1);
        evento("ISAE4", "DIVIDENDO", "B3", "2026-04-17", "2026-04-29", "0.14131100000", 1);
        lancarComSucesso(dono, compra("ISAE4", "2026-03-02", "20", "24.00"));
        Map<String, Object> l = linha("L", "ISAE4", "DIVIDENDO", "2026-04-29", "20", "2.82", null, null);

        Map<String, Object> r = importar(List.of(l, l, l));

        assertThat(situacoes(r)).containsExactly("JA_EXISTE", "JA_EXISTE", "JA_EXISTE");
        assertThat(eventos("ISAE4")).isEqualTo(3);
    }

    @Test
    @DisplayName("Recusa: ativo fora do catalogo e linha incompleta")
    void recusa() {
        Map<String, Object> incompleta = linha("L2", "HGLG11", "RENDIMENTO", "2025-01-15", "10", "11.00", null, null);
        incompleta.put("paymentDate", null);

        Map<String, Object> r = importar(List.of(
            linha("L1", "XXXX11", "RENDIMENTO", "2025-01-15", "10", "11.00", null, null), incompleta));

        assertThat(situacoes(r)).containsExactly("RECUSADO", "RECUSADO");
        assertThat(mensagem(r, 0)).contains("fora do catalogo");
    }

    @Test
    @DisplayName("So administrador")
    void soAdministrador() {
        User outro = usuarioComCarteira("comum@primevest.test");
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("lines", List.of());

        assertThat(post(outro, URL, corpo, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
