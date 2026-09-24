package com.primevest.functional;

import com.primevest.domain.dividend.ProventosSyncService;
import com.primevest.domain.user.User;
import com.primevest.integration.b3.B3Client;
import com.primevest.integration.b3.B3Exception;
import com.primevest.integration.b3.B3Fixtures;
import com.primevest.integration.b3.B3Supplement;
import com.primevest.integration.b3.B3Values;
import com.primevest.integration.b3.CvmFundRegistry;
import com.primevest.integration.b3.FnetClient;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proventos pela B3 de ponta a ponta (PLANO_BRAPI.md, F5): a rotina grava os
 * eventos do papel no catalogo e cada posicao recebe a sua conta.
 *
 * <p>A B3 responde com os arquivos reais gravados em 24/09/2026 (a rede esta
 * bloqueada nos testes e o endpoint nao e documentado).</p>
 */
class ProventosIT extends FuncionalBase {

    @MockBean private B3Client b3Client;
    @MockBean private CvmFundRegistry cvmFundRegistry;
    @MockBean private FnetClient fnetClient;

    @Autowired private ProventosSyncService proventosSyncService;

    private User dono;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("proventos@primevest.test");
        B3Client real = B3Fixtures.client();
        when(b3Client.companySupplement(anyString())).thenAnswer(i -> real.companySupplement(i.getArgument(0)));
        when(b3Client.fundSupplement(anyString())).thenAnswer(i -> real.fundSupplement(i.getArgument(0)));
        when(b3Client.companyDetail(anyString())).thenAnswer(i -> real.companyDetail(i.getArgument(0)));
        when(b3Client.cashDividendsHistory(anyString(), anyInt(), anyInt()))
            .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        jdbc.update("UPDATE assets SET isin = NULL, cnpj = NULL, cash_history_loaded_at = NULL");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> proventos() {
        Map<String, Object> resumo = get(dono, "/api/v1/dividends/", Map.class).getBody();
        return (List<Map<String, Object>>) resumo.get("recent");
    }

    /** O que a B3 diz que PETR4 (ISIN PN) pagou com data-com a partir de uma data, x cotas. */
    private BigDecimal esperadoPetr4(LocalDate desde, int cotas) {
        B3Supplement petr = B3Fixtures.client().companySupplement("PETR");
        return petr.cash().stream()
            .filter(d -> "BRPETRACNPR6".equals(d.isinCode()))
            .filter(d -> !B3Values.date(d.lastDatePrior()).isBefore(desde))
            .map(d -> B3Values.decimal(d.rate()).multiply(BigDecimal.valueOf(cotas)).setScale(2, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal somaProventos() {
        return proventos().stream().map(p -> numero(p.get("totalAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("A rodada grava os eventos da B3 e os proventos da posicao batem com a B3, so no ISIN do papel")
    void proventosBatemComAB3() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-08-01", "100", "45.00"));

        ProventosSyncService.SyncResult r = proventosSyncService.syncAll("teste");

        assertThat(r.failures()).isZero();
        assertThat(jdbc.queryForObject("SELECT isin FROM assets WHERE ticker = 'PETR4'", String.class))
            .isEqualTo("BRPETRACNPR6");
        BigDecimal esperado = esperadoPetr4(LocalDate.of(2026, 8, 1), 100);
        assertThat(esperado).isPositive();
        assertThat(somaProventos()).isEqualByComparingTo(esperado);
        assertThat(proventos()).extracting(p -> p.get("type")).containsOnly("DIVIDENDO", "JCP");
    }

    @Test
    @DisplayName("Compra retroativa so recalcula: os eventos ja estao no catalogo, nenhuma chamada nova a B3")
    void compraRetroativaSoRecalcula() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-08-01", "100", "45.00"));
        proventosSyncService.syncAll("teste");

        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-01-05", "100", "40.00"));

        verify(b3Client, times(1)).companySupplement("PETR");
        BigDecimal esperado = esperadoPetr4(LocalDate.of(2026, 1, 5), 100)
            .add(esperadoPetr4(LocalDate.of(2026, 8, 1), 100));
        // Arredondamento por provento: 200 x valor e 2 x (100 x valor) podem diferir em centavos.
        assertThat(somaProventos()).isCloseTo(esperado, org.assertj.core.data.Offset.offset(new BigDecimal("0.10")));
    }

    @Test
    @DisplayName("B3 fora do ar: a rodada registra a falha e nao apaga nada")
    void b3ForaDoAr() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-08-01", "100", "45.00"));
        proventosSyncService.syncAll("teste");
        BigDecimal antes = somaProventos();

        org.mockito.Mockito.doThrow(new B3Exception("B3 respondeu vazio")).when(b3Client).companySupplement(anyString());
        ProventosSyncService.SyncResult r = proventosSyncService.syncAll("teste");

        assertThat(r.failures()).isEqualTo(1);
        assertThat(somaProventos()).isEqualByComparingTo(antes);
    }

    @Test
    @DisplayName("Amortizacao (B13): entra como provento e reduz o custo da posicao a partir do pagamento")
    void amortizacaoReduzCusto() {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
            SELECT id, 'AMORTIZACAO', 'AMORTIZACAO', DATE '2026-06-30', DATE '2026-07-15', 1.00, 'FNET'
            FROM assets WHERE ticker = ?""", CatalogIds.FII);

        lancarComSucesso(dono, compra(CatalogIds.FII, "2026-06-01", "100", "160.00"));

        Map<String, Object> posicao = posicaoDe(dono, CatalogIds.FII);
        assertThat(numero(posicao.get("totalShares"))).isEqualByComparingTo("100");
        assertThat(numero(posicao.get("totalInvested"))).isEqualByComparingTo("15900.00");
        assertThat(numero(posicao.get("averagePrice"))).isEqualByComparingTo("159.0000");
        assertThat(proventos()).singleElement().satisfies(p -> {
            assertThat(p.get("type")).isEqualTo("AMORTIZACAO");
            assertThat(numero(p.get("totalAmount"))).isEqualByComparingTo("100.00");
        });
    }

    @Test
    @DisplayName("Rotulo desconhecido da B3: o evento fica no catalogo, mas nao gera provento")
    void rotuloDesconhecido() {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, value_per_share, source)
            SELECT id, NULL, 'XPTO NOVO', DATE '2026-08-20', 0.50, 'B3'
            FROM assets WHERE ticker = ?""", CatalogIds.ACAO);

        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-08-01", "100", "45.00"));

        assertThat(proventos()).isEmpty();
    }
}
