package com.primevest.functional;

import com.primevest.domain.dividend.DividendCalendar;
import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.domain.user.User;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3Fixtures;
import com.primevest.adm.integration.b3.FnetClient;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proventos recebidos x a receber, de ponta a ponta. Recebido = pagamento ate
 * hoje (fuso da B3, contando o proprio dia); sem data de pagamento, conforme a
 * origem (Dividend.isReceived). A Carteira e o Dashboard so somam o recebido.
 *
 * <p>Hoje fixo em 24/09/2026 e a B3 gravada na mesma data: PETR4 tem JCP pago
 * em 21/09/2026 (recebido) e JCP/dividendo em 23/11 e 21/12/2026 (a receber).</p>
 */
class ProventosAReceberIT extends CatalogoE2EBase {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 24);
    private static final String BBAS3 = "BBAS3";

    @MockBean private DividendCalendar dividendCalendar;

    @Autowired private ProventosCatalogService proventosCatalogService;

    private User dono;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("a-receber@primevest.test");
        when(dividendCalendar.today()).thenReturn(HOJE);
        B3Client real = B3Fixtures.client();
        when(b3Client.companySupplement(anyString())).thenAnswer(i -> real.companySupplement(i.getArgument(0)));
        when(b3Client.fundSupplement(anyString())).thenAnswer(i -> real.fundSupplement(i.getArgument(0)));
        when(b3Client.companyDetail(anyString())).thenAnswer(i -> real.companyDetail(i.getArgument(0)));
        when(b3Client.cashDividendsHistory(anyString(), anyInt(), anyInt()))
            .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        jdbc.update("UPDATE assets SET isin = NULL");
    }

    // ------------------------------------------------------------------
    // Atalhos
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> resumo() {
        return get(dono, "/api/v1/dividends/", Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> proventos() {
        return (List<Map<String, Object>>) resumo().get("recent");
    }

    private Map<String, Object> provento(String ticker, String tipo, String pagamento) {
        List<Map<String, Object>> achados = proventos().stream()
            .filter(p -> ticker.equals(p.get("ticker")) && tipo.equals(p.get("type")) && pagamento.equals(p.get("paymentDate")))
            .toList();
        assertThat(achados).as("%s %s pago em %s", ticker, tipo, pagamento).isNotEmpty();
        return achados.get(0);
    }

    private static boolean recebido(Map<String, Object> p) {
        return Boolean.TRUE.equals(p.get("received"));
    }

    private BigDecimal liquido(Map<String, Object> p) {
        Object v = p.get("netAmount") != null ? p.get("netAmount") : p.get("totalAmount");
        return numero(v);
    }

    private BigDecimal soma(List<Map<String, Object>> lista, Predicate<Map<String, Object>> filtro, String campo) {
        return lista.stream().filter(filtro).map(p -> p.get(campo) == null ? BigDecimal.ZERO : numero(p.get(campo)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal somaLiquida(List<Map<String, Object>> lista, Predicate<Map<String, Object>> filtro) {
        return lista.stream().filter(filtro).map(this::liquido).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal proventosDaCarteira() {
        return numero(((Map<String, Object>) get(dono, "/api/v1/portfolio/", Map.class).getBody()).get("totalDividends"));
    }

    private void petr4() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-05-04", "50", "38.00"));
        assertThat(proventosCatalogService.syncAll("teste").failures()).isZero();
    }

    private void evento(String ticker, String tipo, String fonte, String dataCom, String pagamento, String valor) {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
            SELECT id, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS NUMERIC), ?
            FROM assets WHERE ticker = ?""", tipo, tipo, dataCom, pagamento, valor, fonte, ticker);
    }

    // ------------------------------------------------------------------
    // Cenarios
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PETR4 em 24/09/2026: JCP pago em 21/09 e recebido; os de 23/11 e 21/12 sao a receber")
    void situacaoPorPagamento() {
        petr4();

        assertThat(recebido(provento(CatalogIds.ACAO, "JCP", "2026-09-21"))).isTrue();
        assertThat(recebido(provento(CatalogIds.ACAO, "JCP", "2026-11-23"))).isFalse();
        assertThat(recebido(provento(CatalogIds.ACAO, "JCP", "2026-12-21"))).isFalse();
        assertThat(recebido(provento(CatalogIds.ACAO, "DIVIDENDO", "2026-12-21"))).isFalse();
    }

    @Test
    @DisplayName("D0: o provento pago hoje ja conta como recebido")
    void pagoHojeERecebido() {
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 21));
        petr4();

        assertThat(recebido(provento(CatalogIds.ACAO, "JCP", "2026-09-21"))).isTrue();
    }

    @Test
    @DisplayName("Virada do dia: o a receber passa a recebido sozinho, sem recalculo")
    void viradaDoDia() {
        evento(BBAS3, "DIVIDENDO", "B3", "2026-09-10", "2026-09-25", "0.50");
        lancarComSucesso(dono, compra(BBAS3, "2026-09-01", "100", "22.00"));
        assertThat(recebido(proventos().get(0))).isFalse();
        assertThat(proventosDaCarteira()).isEqualByComparingTo("0");

        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 25));

        assertThat(recebido(proventos().get(0))).isTrue();
        assertThat(proventosDaCarteira()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Carteira e Dashboard ignoram o que ainda sera pago")
    void carteiraIgnoraAReceber() {
        petr4();
        List<Map<String, Object>> lista = proventos();
        BigDecimal recebidoLiquido = somaLiquida(lista, ProventosAReceberIT::recebido);
        BigDecimal tudo = somaLiquida(lista, p -> true);
        assertThat(recebidoLiquido).isLessThan(tudo);

        assertThat(numero(posicaoDe(dono, CatalogIds.ACAO).get("totalDividends"))).isEqualByComparingTo(recebidoLiquido);
        assertThat(proventosDaCarteira()).isEqualByComparingTo(recebidoLiquido);
    }

    @Test
    @DisplayName("Posicao so com provento futuro (o caso TIMS3) mostra zero na Carteira")
    void soProventoFuturo() {
        evento(BBAS3, "JCP", "B3", "2026-09-22", "2027-01-22", "0.21668011");
        lancarComSucesso(dono, compra(BBAS3, "2026-09-01", "75", "22.00"));

        assertThat(proventos()).singleElement().satisfies(p -> assertThat(recebido(p)).isFalse());
        assertThat(numero(posicaoDe(dono, BBAS3).get("totalDividends"))).isEqualByComparingTo("0");
        assertThat(proventosDaCarteira()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Resumo: recebido e a receber separados, e os dois somam tudo")
    void resumoSeparado() {
        petr4();
        Map<String, Object> resumo = resumo();
        List<Map<String, Object>> lista = proventos();
        Predicate<Map<String, Object>> aReceber = p -> !recebido(p);

        assertThat(numero(resumo.get("totalDividends"))).isEqualByComparingTo(somaLiquida(lista, ProventosAReceberIT::recebido));
        assertThat(numero(resumo.get("totalGross"))).isEqualByComparingTo(soma(lista, ProventosAReceberIT::recebido, "totalAmount"));
        assertThat(numero(resumo.get("totalTax"))).isEqualByComparingTo(soma(lista, ProventosAReceberIT::recebido, "withholdingTax"));
        assertThat(numero(resumo.get("totalToReceive"))).isEqualByComparingTo(somaLiquida(lista, aReceber)).isPositive();
        assertThat(numero(resumo.get("toReceiveGross"))).isEqualByComparingTo(soma(lista, aReceber, "totalAmount"));
        assertThat(numero(resumo.get("toReceiveTax"))).isEqualByComparingTo(soma(lista, aReceber, "withholdingTax"));
        assertThat(numero(resumo.get("totalDividends")).add(numero(resumo.get("totalToReceive"))))
            .isEqualByComparingTo(somaLiquida(lista, p -> true));
        @SuppressWarnings("unchecked")
        Map<String, Object> porTipo = (Map<String, Object>) resumo.get("byType");
        assertThat(numero(porTipo.get("JCP"))).isEqualByComparingTo(
            somaLiquida(lista, p -> recebido(p) && "JCP".equals(p.get("type"))));
    }

    @Test
    @DisplayName("Sem data de pagamento: recebido se veio do historico longo; a receber se veio da fonte recente")
    void semDataConformeAOrigem() {
        evento(BBAS3, "DIVIDENDO", "B3_HISTORICO", "2025-03-11", null, "0.10");
        evento(BBAS3, "JCP", "B3", "2026-09-01", null, "0.20");
        lancarComSucesso(dono, compra(BBAS3, "2025-01-10", "100", "22.00"));

        assertThat(proventos()).hasSize(2).allSatisfy(p -> {
            if ("DIVIDENDO".equals(p.get("type"))) {
                assertThat(recebido(p)).as("historico longo, sem data").isTrue();
            } else {
                assertThat(recebido(p)).as("fonte recente, pagamento a definir").isFalse();
            }
        });
        assertThat(proventosDaCarteira()).isEqualByComparingTo("10.00");
        assertThat(numero(resumo().get("totalToReceive"))).isPositive();
    }
}
