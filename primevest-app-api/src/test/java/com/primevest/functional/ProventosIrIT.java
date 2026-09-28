package com.primevest.functional;

import com.primevest.domain.dividend.DividendCalendar;
import com.primevest.domain.dividend.DividendTaxVerifier;
import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.domain.user.User;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3Fixtures;
import com.primevest.adm.integration.b3.CvmFundRegistry;
import com.primevest.adm.integration.b3.FnetClient;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * IR retido na fonte sobre proventos, de ponta a ponta (PLANO_IR_PROVENTOS.md,
 * 5.2): uma regra por teste.
 *
 * <p>A B3 responde com os arquivos reais gravados em 24/09/2026 —
 * {@code company_PETR.json} traz exatamente os proventos de PETR4 conferidos no
 * extrato da B3 (secao 2 do plano). Com 50 cotas, o teste reproduz o extrato
 * centavo a centavo.</p>
 */
class ProventosIrIT extends CatalogoE2EBase {

    private static final String BBAS3 = "BBAS3";
    private static final String ETF = "DIVD11";
    private static final String LEI_DE_TESTE = "Lei hipotetica (teste F12)";

    /** Hoje fixo: o dia da conferencia com o extrato. Recebido x a receber depende dele. */
    @MockBean private DividendCalendar dividendCalendar;

    @Autowired private ProventosCatalogService proventosCatalogService;
    @Autowired private DividendTaxVerifier dividendTaxVerifier;

    private User dono;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("ir-proventos@primevest.test");
        when(dividendCalendar.today()).thenReturn(LocalDate.of(2026, 9, 24));
        B3Client real = B3Fixtures.client();
        when(b3Client.companySupplement(anyString())).thenAnswer(i -> real.companySupplement(i.getArgument(0)));
        when(b3Client.fundSupplement(anyString())).thenAnswer(i -> real.fundSupplement(i.getArgument(0)));
        when(b3Client.companyDetail(anyString())).thenAnswer(i -> real.companyDetail(i.getArgument(0)));
        when(b3Client.cashDividendsHistory(anyString(), anyInt(), anyInt()))
            .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        jdbc.update("UPDATE assets SET isin = NULL, cnpj = NULL, cash_history_loaded_at = NULL");
    }

    /** Desfaz a "lei nova" do F12: as regras sao catalogo e valem para os outros testes. */
    @AfterEach
    void restaurarRegras() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM withholding_tax_rules WHERE legal_basis = ?", Integer.class,
                LEI_DE_TESTE) > 0) {
            jdbc.execute("TRUNCATE TABLE dividends");
            jdbc.update("DELETE FROM withholding_tax_rules WHERE legal_basis = ?", LEI_DE_TESTE);
            jdbc.update("UPDATE withholding_tax_rules SET valid_to = NULL WHERE dividend_type = 'JCP' AND valid_from = DATE '2026-01-01'");
        }
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

    /** O provento do extrato: ativo, tipo e dia do pagamento. */
    private Map<String, Object> provento(String ticker, String tipo, String pagamento) {
        List<Map<String, Object>> achados = proventos().stream()
            .filter(p -> ticker.equals(p.get("ticker")) && tipo.equals(p.get("type")) && pagamento.equals(p.get("paymentDate")))
            .toList();
        assertThat(achados).as("%s %s pago em %s", ticker, tipo, pagamento).hasSize(1);
        return achados.get(0);
    }

    private BigDecimal campo(Map<String, Object> p, String nome) {
        Object v = p.get(nome);
        return v == null ? null : numero(v);
    }

    private void confere(Map<String, Object> p, String bruto, String aliquota, String ir, String liquido) {
        assertThat(campo(p, "totalAmount")).as("bruto").isEqualByComparingTo(bruto);
        assertThat(campo(p, "withholdingRate")).as("aliquota").isEqualByComparingTo(aliquota);
        assertThat(campo(p, "withholdingTax")).as("IR").isEqualByComparingTo(ir);
        assertThat(campo(p, "netAmount")).as("liquido").isEqualByComparingTo(liquido);
    }

    /** PETR4, 50 cotas desde antes de dez/2025, e a rotina de proventos com a B3 gravada. */
    private void petr4NoExtrato() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2025-12-01", "50", "35.00"));
        ProventosCatalogService.SyncResult r = proventosCatalogService.syncAll("teste");
        assertThat(r.failures()).isZero();
    }

    private void evento(String ticker, String tipo, String aprovacao, String dataCom, String pagamento, String valor) {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, approved_on, data_com, payment_date, value_per_share, source)
            SELECT id, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS DATE), CAST(? AS NUMERIC), 'B3'
            FROM assets WHERE ticker = ?""", tipo, tipo, aprovacao, dataCom, pagamento, valor, ticker);
    }

    private Integer semIr() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dividends WHERE tax_checked_at IS NULL", Integer.class);
    }

    // ------------------------------------------------------------------
    // F1–F5: PETR4 igual ao extrato da B3
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F1 — JCP creditado em 2025 e pago em 20/02/2026 retem 15%: 20,05")
    void f1JcpCreditadoEm2025() {
        petr4NoExtrato();

        Map<String, Object> p = provento(CatalogIds.ACAO, "JCP", "2026-02-20");
        confere(p, "23.58", "0.15", "3.53", "20.05");
        assertThat(campo(p, "netAmountPerShare")).isEqualByComparingTo("0.40086321");
        assertThat(campo(p, "amountPerShare")).isEqualByComparingTo("0.47160378");
    }

    @Test
    @DisplayName("F2 — JCP de 20/03/2026: bruto truncado 8,75 (nao 8,76) e liquido 7,44")
    void f2BrutoTruncado() {
        petr4NoExtrato();

        confere(provento(CatalogIds.ACAO, "JCP", "2026-03-20"), "8.75", "0.15", "1.31", "7.44");
    }

    @Test
    @DisplayName("F3 — Dividendo de 20/03/2026 e isento: 14,82")
    void f3DividendoIsento() {
        petr4NoExtrato();

        confere(provento(CatalogIds.ACAO, "DIVIDENDO", "2026-03-20"), "14.82", "0", "0", "14.82");
    }

    @Test
    @DisplayName("F4 — JCP creditado em 2026 (pago em 22/06/2026) retem 17,5%: 12,92")
    void f4JcpCreditadoEm2026() {
        petr4NoExtrato();

        Map<String, Object> p = provento(CatalogIds.ACAO, "JCP", "2026-06-22");
        confere(p, "15.65", "0.175", "2.73", "12.92");
        assertThat(p.get("withholdingLegalBasis").toString()).contains("LC 224/2025");
    }

    @Test
    @DisplayName("F5 — Rendimento de acao (atualizacao pela Selic) de 22/06/2026 retem 22,5%: 0,79")
    void f5RendimentoDeAcao() {
        petr4NoExtrato();

        confere(provento(CatalogIds.ACAO, "RENDIMENTO", "2026-06-22"), "1.01", "0.225", "0.22", "0.79");
    }

    // ------------------------------------------------------------------
    // F6–F10: as outras regras
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F6 — BBAS3, 125 cotas, JCP de 11/06/2026 em duas parcelas: 8,41 + 6,16 = 14,57")
    void f6Bbas3() {
        evento(BBAS3, "JCP", "2026-05-14", "2026-06-01", "2026-06-11", "0.08157785203");
        evento(BBAS3, "JCP", "2026-05-14", "2026-06-01", "2026-06-11", "0.05968401166");

        lancarComSucesso(dono, compra(BBAS3, "2026-05-04", "125", "22.00"));

        List<Map<String, Object>> jcp = proventos().stream().filter(p -> BBAS3.equals(p.get("ticker"))).toList();
        assertThat(jcp).extracting(p -> campo(p, "netAmount"))
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactlyInAnyOrder(new BigDecimal("8.41"), new BigDecimal("6.16"));
        assertThat(jcp.stream().map(p -> campo(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("14.57");
    }

    @Test
    @DisplayName("F7 — Rendimento de FII e isento: TOTAL igual ao bruto")
    void f7RendimentoDeFii() {
        evento(CatalogIds.FII, "RENDIMENTO", "2026-08-29", "2026-08-29", "2026-09-15", "1.10");

        lancarComSucesso(dono, compra(CatalogIds.FII, "2026-08-01", "10", "160.00"));

        assertThat(proventos()).singleElement()
            .satisfies(p -> confere(p, "11.00", "0", "0", "11.00"));
    }

    @Test
    @DisplayName("F8 — Amortizacao de FII e isenta e continua reduzindo o custo")
    void f8Amortizacao() {
        evento(CatalogIds.FII, "AMORTIZACAO", null, "2026-06-30", "2026-07-15", "1.00");

        lancarComSucesso(dono, compra(CatalogIds.FII, "2026-06-01", "100", "160.00"));

        assertThat(proventos()).singleElement()
            .satisfies(p -> confere(p, "100.00", "0", "0", "100.00"));
        assertThat(numero(posicaoDe(dono, CatalogIds.FII).get("totalInvested"))).isEqualByComparingTo("15900.00");
    }

    @Test
    @DisplayName("F9 — Restituicao de capital de acao e isenta")
    void f9RestituicaoDeCapital() {
        evento(CatalogIds.ACAO, "RESTITUICAO_CAPITAL", null, "2026-06-30", "2026-07-15", "0.50");

        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-06-01", "10", "35.00"));

        assertThat(proventos()).singleElement()
            .satisfies(p -> confere(p, "5.00", "0", "0", "5.00"));
    }

    @Test
    @DisplayName("F10 — ETF nao tem regra: aliquota e liquido nulos, e a carteira soma o bruto")
    void f10SemRegra() {
        evento(ETF, "RENDIMENTO", "2026-09-04", "2026-09-04", "2026-09-15", "0.79830000000");

        lancarComSucesso(dono, compra(ETF, "2026-09-01", "10", "64.00"));

        assertThat(proventos()).singleElement().satisfies(p -> {
            assertThat(campo(p, "totalAmount")).isEqualByComparingTo("7.98");
            assertThat(p.get("withholdingRate")).isNull();
            assertThat(p.get("withholdingTax")).isNull();
            assertThat(p.get("netAmountPerShare")).isNull();
            assertThat(p.get("netAmount")).isNull();
        });
        assertThat(semIr()).isZero();
        assertThat(numero(posicaoDe(dono, ETF).get("totalDividends"))).isEqualByComparingTo("7.98");
    }

    // ------------------------------------------------------------------
    // F11–F13: proventos novos, lei nova e verificador
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F11 — Proventos novos (rotina e compra retroativa) nascem com o IR calculado")
    void f11ProventosNovos() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-08-01", "50", "40.00"));
        proventosCatalogService.syncAll("teste");
        int depoisDaRotina = proventos().size();
        assertThat(depoisDaRotina).isPositive();
        assertThat(semIr()).isZero();

        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2025-12-01", "50", "35.00"));

        assertThat(proventos().size()).isGreaterThan(depoisDaRotina);
        assertThat(semIr()).isZero();
        confere(provento(CatalogIds.ACAO, "JCP", "2026-02-20"), "23.58", "0.15", "3.53", "20.05");
    }

    @Test
    @DisplayName("F12 — Lei nova por migration: so os JCP creditados na nova vigencia mudam")
    void f12LeiNova() {
        petr4NoExtrato();

        // O que uma migration de lei nova faz (cabecalho da V23).
        jdbc.update("UPDATE withholding_tax_rules SET valid_to = DATE '2026-06-30' WHERE dividend_type = 'JCP' AND valid_from = DATE '2026-01-01'");
        jdbc.update("""
            INSERT INTO withholding_tax_rules (dividend_type, asset_type_id, rate, base_date, valid_from, valid_to, legal_basis)
            SELECT 'JCP', id, 0.2000, 'CREDIT', DATE '2026-07-01', NULL, ? FROM asset_types WHERE name = 'ACAO'""",
            LEI_DE_TESTE);
        jdbc.update("UPDATE dividends SET tax_checked_at = NULL WHERE type = 'JCP'");

        assertThat(dividendTaxVerifier.verify()).isPositive();

        // Creditado em 06/08/2026: 20%. 0,67407131 x 50 = 33,70; IR 6,74.
        confere(provento(CatalogIds.ACAO, "JCP", "2026-11-23"), "33.70", "0.20", "6.74", "26.96");
        // Creditados antes da nova vigencia: como estavam.
        confere(provento(CatalogIds.ACAO, "JCP", "2026-06-22"), "15.65", "0.175", "2.73", "12.92");
        confere(provento(CatalogIds.ACAO, "JCP", "2026-02-20"), "23.58", "0.15", "3.53", "20.05");
    }

    @Test
    @DisplayName("F13 — Verificador na subida: proventos sem IR voltam preenchidos, com os mesmos valores")
    void f13Verificador() {
        petr4NoExtrato();
        Function<List<Map<String, Object>>, Map<Object, BigDecimal>> liquidos = lista -> lista.stream()
            .collect(Collectors.toMap(p -> p.get("id"), p -> campo(p, "netAmount")));
        Map<Object, BigDecimal> antes = liquidos.apply(proventos());

        jdbc.update("UPDATE dividends SET tax_checked_at = NULL");
        assertThat(semIr()).isPositive();

        assertThat(dividendTaxVerifier.verify()).isEqualTo(1);
        assertThat(semIr()).isZero();
        Map<Object, BigDecimal> depois = liquidos.apply(proventos());
        assertThat(depois.keySet()).isEqualTo(antes.keySet());
        antes.forEach((id, valor) -> assertThat(depois.get(id)).isEqualByComparingTo(valor));
        assertThat(dividendTaxVerifier.verify()).isZero();
    }

    // ------------------------------------------------------------------
    // F14–F15: o que a tela le
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F14 — Carteira e Dashboard somam o provento liquido (so o ja recebido)")
    void f14CarteiraEmLiquido() {
        petr4NoExtrato();
        List<Map<String, Object>> recebidos = proventos().stream().filter(p -> Boolean.TRUE.equals(p.get("received"))).toList();
        BigDecimal liquido = recebidos.stream().map(p -> campo(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bruto = recebidos.stream().map(p -> campo(p, "totalAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(liquido).isLessThan(bruto);

        assertThat(numero(posicaoDe(dono, CatalogIds.ACAO).get("totalDividends"))).isEqualByComparingTo(liquido);
        @SuppressWarnings("unchecked")
        Map<String, Object> carteira = get(dono, "/api/v1/portfolio/", Map.class).getBody();
        assertThat(numero(carteira.get("totalDividends"))).isEqualByComparingTo(liquido);
    }

    @Test
    @DisplayName("F15 — Resumo da tela de proventos: totais e byType em liquido; bruto e IR a parte")
    void f15ResumoDaTela() {
        petr4NoExtrato();
        Map<String, Object> resumo = resumo();
        List<Map<String, Object>> todos = proventos();
        // Os totais do resumo sao do recebido; o a receber tem bloco proprio (ProventosAReceberIT).
        List<Map<String, Object>> lista = todos.stream().filter(p -> Boolean.TRUE.equals(p.get("received"))).toList();
        BigDecimal liquido = lista.stream().map(p -> campo(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bruto = lista.stream().map(p -> campo(p, "totalAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ir = lista.stream().map(p -> campo(p, "withholdingTax")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal jcp = lista.stream().filter(p -> "JCP".equals(p.get("type")))
            .map(p -> campo(p, "netAmount")).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(numero(resumo.get("totalDividends"))).isEqualByComparingTo(liquido);
        assertThat(numero(resumo.get("totalGross"))).isEqualByComparingTo(bruto);
        assertThat(numero(resumo.get("totalTax"))).isEqualByComparingTo(ir);
        assertThat(bruto.subtract(ir)).isEqualByComparingTo(liquido);
        @SuppressWarnings("unchecked")
        Map<String, Object> porTipo = (Map<String, Object>) resumo.get("byType");
        assertThat(numero(porTipo.get("JCP"))).isEqualByComparingTo(jcp);
        assertThat(todos).allSatisfy(p -> {
            assertThat(p.get("amountPerShare")).isNotNull();
            assertThat(p.get("withholdingRate")).isNotNull();
            assertThat(p.get("netAmountPerShare")).isNotNull();
        });
    }
}
