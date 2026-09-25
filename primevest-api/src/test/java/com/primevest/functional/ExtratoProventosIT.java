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
 * Importacao do extrato de proventos da B3 (V24): POST /admin/proventos/extrato.
 * Cada linha do extrato ja existe, completa a data de um provento sem
 * pagamento ou vira evento novo — e nada duplica.
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

    private static Map<String, Object> deduzido(String ticker, String tipo, String dataCom, String cotas,
                                                String liquido, String pagamento) {
        Map<String, Object> d = new HashMap<>();
        d.put("ref", "deduzido");
        d.put("ticker", ticker);
        d.put("type", tipo);
        d.put("dataCom", dataCom);
        d.put("shares", new BigDecimal(cotas));
        d.put("netAmount", new BigDecimal(liquido));
        d.put("paymentDate", pagamento);
        return d;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> importar(boolean simular, List<Map<String, Object>> linhas,
                                         List<Map<String, Object>> deduzidos) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("dryRun", simular);
        corpo.put("lines", linhas);
        corpo.put("deducedPayments", deduzidos);
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
    @DisplayName("Evento novo: rendimento de FII sem nada no PrimeVest vira evento EXTRATO_B3 e provento recebido")
    void eventoNovo() {
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, Object> r = importar(false,
            List.of(linha("L1", "HGLG11", "RENDIMENTO", "2025-01-15", "10", "11.00", "11.00", "2024-12-30")), List.of());

        assertThat(situacoes(r)).containsExactly("EVENTO_CRIADO");
        assertThat(proventos()).singleElement().satisfies(p -> {
            assertThat(p.get("exDate")).isEqualTo("2024-12-30");
            assertThat(p.get("paymentDate")).isEqualTo("2025-01-15");
            assertThat(numero(p.get("netAmount"))).isEqualByComparingTo("11.00");
            assertThat(numero(p.get("amountPerShare"))).isEqualByComparingTo("1.10");
            assertThat(p.get("cashEventSource")).isEqualTo("EXTRATO_B3");
            assertThat(p.get("paymentDateSource")).isEqualTo("EXTRATO_B3");
            assertThat(p.get("received")).isEqualTo(true);
        });
    }

    @Test
    @DisplayName("Repetivel: a segunda importacao do mesmo extrato encontra tudo e nao duplica")
    void repetivel() {
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));
        List<Map<String, Object>> linhas =
            List.of(linha("L1", "HGLG11", "RENDIMENTO", "2025-01-15", "10", "11.00", "11.00", "2024-12-30"));

        importar(false, linhas, List.of());
        Map<String, Object> segunda = importar(false, linhas, List.of());

        assertThat(situacoes(segunda)).containsExactly("JA_EXISTE");
        assertThat(proventos()).hasSize(1);
        assertThat(eventos("HGLG11")).isEqualTo(1);
    }

    @Test
    @DisplayName("Simulacao: relata o que faria e nao grava nada")
    void simulacao() {
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, Object> r = importar(true,
            List.of(linha("L1", "HGLG11", "RENDIMENTO", "2025-01-15", "10", "11.00", "11.00", "2024-12-30")), List.of());

        assertThat(r.get("dryRun")).isEqualTo(true);
        assertThat(situacoes(r)).containsExactly("EVENTO_CRIADO");
        assertThat(proventos()).isEmpty();
        assertThat(eventos("HGLG11")).isZero();
    }

    @Test
    @DisplayName("Completa a data: provento do historico longo sem pagamento recebe a data do extrato")
    void completaData() {
        evento("BBAS3", "JCP", "B3_HISTORICO", "2024-12-11", null, "0.17649109403", 1);
        lancarComSucesso(dono, compra("BBAS3", "2024-12-02", "19", "28.00"));

        Map<String, Object> r = importar(false,
            List.of(linha("L1", "BBAS3", "JCP", "2024-12-27", "19", "2.85", null, null)), List.of());

        assertThat(situacoes(r)).containsExactly("DATA_COMPLETADA");
        assertThat(proventos()).singleElement().satisfies(p -> {
            assertThat(p.get("paymentDate")).isEqualTo("2024-12-27");
            assertThat(p.get("paymentDateSource")).isEqualTo("EXTRATO_B3");
            assertThat(p.get("cashEventSource")).isEqualTo("B3_HISTORICO");
            assertThat(numero(p.get("netAmount"))).isEqualByComparingTo("2.85");
        });
    }

    @Test
    @DisplayName("Parcelas: a paga em junho vai para a parcela 1; a de dezembro, deduzida, para a 2 — e fica a receber")
    void parcelasEDeduzida() {
        evento("CMIG4", "JCP", "B3_HISTORICO", "2025-03-25", null, "0.09455892373", 1);
        evento("CMIG4", "JCP", "B3_HISTORICO", "2025-03-25", null, "0.09455892373", 2);
        lancarComSucesso(dono, compra("CMIG4", "2025-03-03", "100", "11.00"));

        Map<String, Object> r = importar(false,
            List.of(linha("L1", "CMIG4", "JCP", "2026-06-30", "100", "8.04", null, null)),
            List.of(deduzido("CMIG4", "JCP", "2025-03-25", "100", "8.04", "2026-12-30")));

        assertThat(situacoes(r)).containsExactly("DATA_COMPLETADA", "DATA_DEDUZIDA");
        Map<Object, Object> porParcela = new HashMap<>();
        jdbc.queryForList("""
            SELECT e.seq, e.payment_date, e.payment_date_source FROM asset_cash_events e
            JOIN assets a ON a.id = e.asset_id WHERE a.ticker = 'CMIG4'""")
            .forEach(m -> porParcela.put(((Number) m.get("seq")).intValue(),
                m.get("payment_date") + "/" + m.get("payment_date_source")));
        assertThat(porParcela).containsEntry(1, "2026-06-30/EXTRATO_B3").containsEntry(2, "2026-12-30/DEDUZIDA");
        assertThat(proventos()).extracting(p -> p.get("received")).containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("Recusa: cotas na data-com deduzida nao batem com o extrato — nada e criado")
    void cotasNaoBatem() {
        lancarComSucesso(dono, compra("HGLG11", "2024-12-16", "10", "160.00"));

        Map<String, Object> r = importar(false,
            List.of(linha("L1", "HGLG11", "RENDIMENTO", "2025-01-15", "20", "22.00", "22.00", "2024-12-30")), List.of());

        assertThat(situacoes(r)).containsExactly("RECUSADO");
        assertThat(mensagem(r, 0)).contains("10 cotas").contains("20");
        assertThat(eventos("HGLG11")).isZero();
    }

    @Test
    @DisplayName("Recusa: mais de um provento sem data (datas-com diferentes) se encaixa")
    void ambiguo() {
        evento("CMIG4", "JCP", "B3_HISTORICO", "2025-03-25", null, "0.10", 1);
        evento("CMIG4", "JCP", "B3_HISTORICO", "2025-06-23", null, "0.10", 1);
        lancarComSucesso(dono, compra("CMIG4", "2025-03-03", "100", "11.00"));

        Map<String, Object> r = importar(false,
            List.of(linha("L1", "CMIG4", "JCP", "2026-06-30", "100", "8.50", null, null)), List.of());

        assertThat(situacoes(r)).containsExactly("RECUSADO");
        assertThat(mensagem(r, 0)).contains("Mais de um provento");
        assertThat(proventos()).allSatisfy(p -> assertThat(p.get("paymentDate")).isNull());
    }

    @Test
    @DisplayName("Tres linhas iguais no mesmo dia (ISAE4) que ja existem: tres JA_EXISTE, nada duplica")
    void tresLinhasIguaisJaExistentes() {
        evento("ISAE4", "DIVIDENDO", "B3", "2026-03-12", "2026-04-29", "0.14131100000", 1);
        evento("ISAE4", "DIVIDENDO", "B3", "2026-04-02", "2026-04-29", "0.14131100000", 1);
        evento("ISAE4", "DIVIDENDO", "B3", "2026-04-17", "2026-04-29", "0.14131100000", 1);
        lancarComSucesso(dono, compra("ISAE4", "2026-03-02", "20", "24.00"));
        Map<String, Object> l = linha("L", "ISAE4", "DIVIDENDO", "2026-04-29", "20", "2.82", null, null);

        Map<String, Object> r = importar(false, List.of(l, l, l), List.of());

        assertThat(situacoes(r)).containsExactly("JA_EXISTE", "JA_EXISTE", "JA_EXISTE");
        assertThat(proventos()).hasSize(3);
        assertThat(eventos("ISAE4")).isEqualTo(3);
    }

    @Test
    @DisplayName("Rendimento de acao: 22,5% confere o liquido; o mesmo valor pago de novo vira parcela 2; bruto errado e recusado")
    void rendimentoDeAcao() {
        lancarComSucesso(dono, compra("PETR4", "2025-12-01", "50", "35.00"));

        Map<String, Object> r = importar(false, List.of(
            linha("L1", "PETR4", "RENDIMENTO", "2026-02-20", "50", "0.35", "0.45", "2025-12-22"),
            linha("L2", "PETR4", "RENDIMENTO", "2026-03-20", "50", "0.35", "0.45", "2025-12-22"),
            linha("L3", "PETR4", "RENDIMENTO", "2026-03-20", "50", "0.21", "0.26", "2025-12-22"),
            linha("L4", "PETR4", "RENDIMENTO", "2026-04-20", "50", "0.35", "0.46", "2025-12-22")), List.of());

        assertThat(situacoes(r)).containsExactly("EVENTO_CRIADO", "EVENTO_CRIADO", "EVENTO_CRIADO", "RECUSADO");
        assertThat(mensagem(r, 1)).contains("Parcela 2");
        assertThat(mensagem(r, 3)).contains("Liquido calculado 0.36");
        assertThat(proventos()).extracting(p -> numero(p.get("netAmount")).toPlainString())
            .containsExactlyInAnyOrder("0.35", "0.35", "0.21");
        assertThat(proventos()).allSatisfy(p -> assertThat(numero(p.get("withholdingRate"))).isEqualByComparingTo("0.225"));
    }

    @Test
    @DisplayName("So administrador")
    void soAdministrador() {
        User outro = usuarioComCarteira("comum@primevest.test");
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("dryRun", true);
        corpo.put("lines", List.of());

        assertThat(post(outro, URL, corpo, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
