package com.activest.functional;

import com.activest.adm.domain.dividend.DividendCatalogService;
import com.activest.domain.user.User;
import com.activest.adm.integration.b3.B3Client;
import com.activest.adm.integration.b3.B3Exception;
import com.activest.adm.integration.b3.B3Fixtures;
import com.activest.adm.integration.b3.B3Supplement;
import com.activest.adm.integration.b3.B3Values;
import com.activest.adm.integration.b3.FnetClient;
import com.activest.support.CatalogIds;
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
 * Proventos pela B3 de ponta a ponta (docs/planos/PLANO_BRAPI.md, F5): a rotina grava os
 * eventos do papel no catalogo e cada posicao recebe a sua conta.
 *
 * <p>A B3 responde com os arquivos reais gravados em 24/09/2026 (a rede esta
 * bloqueada nos testes e o endpoint nao e documentado).</p>
 */
class DividendsIT extends CatalogE2EBase {


    @Autowired private DividendCatalogService dividendCatalogService;

    private User owner;

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("proventos@activest.test");
        B3Client real = B3Fixtures.client();
        when(b3Client.companySupplement(anyString())).thenAnswer(i -> real.companySupplement(i.getArgument(0)));
        when(b3Client.fundSupplement(anyString())).thenAnswer(i -> real.fundSupplement(i.getArgument(0)));
        when(b3Client.companyDetail(anyString())).thenAnswer(i -> real.companyDetail(i.getArgument(0)));
        when(b3Client.cashDividendsHistory(anyString(), anyInt(), anyInt()))
            .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        jdbc.update("UPDATE assets SET isin = NULL");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dividends() {
        Map<String, Object> summary = get(owner, "/api/v1/dividends/", Map.class).getBody();
        return (List<Map<String, Object>>) summary.get("recent");
    }

    /** O BRUTO que a B3 diz que PETR4 (ISIN PN) pagou com data-com a partir de uma data, x cotas. */
    private BigDecimal expectedPetr4(LocalDate since, int shares) {
        B3Supplement petr = B3Fixtures.client().companySupplement("PETR");
        return petr.cash().stream()
            .filter(d -> "BRPETRACNPR6".equals(d.isinCode()))
            .filter(d -> !B3Values.date(d.lastDatePrior()).isBefore(since))
            .map(d -> B3Values.decimal(d.rate()).multiply(BigDecimal.valueOf(shares)).setScale(2, RoundingMode.DOWN)) // bruto truncado, como a B3
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumDividends() {
        return dividends().stream().map(p -> number(p.get("totalAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("A rodada grava os eventos da B3 e os proventos da posicao batem com a B3, so no ISIN do papel")
    void dividendsMatchB3() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-08-01", "100", "45.00"));

        DividendCatalogService.SyncResult r = dividendCatalogService.syncAll("teste");

        assertThat(r.failures()).isZero();
        assertThat(jdbc.queryForObject("SELECT isin FROM assets WHERE ticker = 'PETR4'", String.class))
            .isEqualTo("BRPETRACNPR6");
        BigDecimal expected = expectedPetr4(LocalDate.of(2026, 8, 1), 100);
        assertThat(expected).isPositive();
        assertThat(sumDividends()).isEqualByComparingTo(expected);
        assertThat(dividends()).extracting(p -> p.get("type")).containsOnly("DIVIDENDO", "JCP");
    }

    @Test
    @DisplayName("Compra retroativa so recalcula: os eventos ja estao no catalogo, nenhuma chamada nova a B3")
    void retroactiveBuyOnlyRecalculates() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-08-01", "100", "45.00"));
        dividendCatalogService.syncAll("teste");

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-01-05", "100", "40.00"));

        verify(b3Client, times(1)).companySupplement("PETR");
        BigDecimal expected = expectedPetr4(LocalDate.of(2026, 1, 5), 100)
            .add(expectedPetr4(LocalDate.of(2026, 8, 1), 100));
        // Arredondamento por provento: 200 x valor e 2 x (100 x valor) podem diferir em centavos.
        assertThat(sumDividends()).isCloseTo(expected, org.assertj.core.data.Offset.offset(new BigDecimal("0.10")));
    }

    @Test
    @DisplayName("B3 fora do ar: a rodada registra a falha e nao apaga nada")
    void b3Down() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-08-01", "100", "45.00"));
        dividendCatalogService.syncAll("teste");
        BigDecimal before = sumDividends();

        org.mockito.Mockito.doThrow(new B3Exception("B3 respondeu vazio")).when(b3Client).companySupplement(anyString());
        DividendCatalogService.SyncResult r = dividendCatalogService.syncAll("teste");

        assertThat(r.failures()).isEqualTo(1);
        assertThat(sumDividends()).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("Amortizacao (B13): entra como provento e reduz o custo da posicao a partir do pagamento")
    void amortizationReducesCost() {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
            SELECT id, 'AMORTIZACAO', 'AMORTIZACAO', DATE '2026-06-30', DATE '2026-07-15', 1.00, 'FNET'
            FROM assets WHERE ticker = ?""", CatalogIds.FII);

        recordSuccessfully(owner, buy(CatalogIds.FII, "2026-06-01", "100", "160.00"));

        Map<String, Object> position = positionOf(owner, CatalogIds.FII);
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("100");
        assertThat(number(position.get("totalInvested"))).isEqualByComparingTo("15900.00");
        assertThat(number(position.get("averagePrice"))).isEqualByComparingTo("159.0000");
        assertThat(dividends()).singleElement().satisfies(p -> {
            assertThat(p.get("type")).isEqualTo("AMORTIZACAO");
            assertThat(number(p.get("totalAmount"))).isEqualByComparingTo("100.00");
        });
    }

    @Test
    @DisplayName("Rotulo desconhecido da B3: o evento fica no catalogo, mas nao gera provento")
    void unknownLabel() {
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, value_per_share, source)
            SELECT id, NULL, 'XPTO NOVO', DATE '2026-08-20', 0.50, 'B3'
            FROM assets WHERE ticker = ?""", CatalogIds.STOCK);

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-08-01", "100", "45.00"));

        assertThat(dividends()).isEmpty();
    }

    @Test
    @DisplayName("FII que ainda nao distribuiu (APXR11, resposta real da B3 sem proventos): rodada sem falha, carteira com zero")
    void fundWithoutDistribution() {
        recordSuccessfully(owner, buy("APXR11", "2026-08-01", "10", "9.00"));

        DividendCatalogService.SyncResult r = dividendCatalogService.syncAll("teste");

        assertThat(r.failures()).isZero();
        assertThat(r.newCashEvents()).isZero();
        assertThat(dividends()).isEmpty();
        Map<String, Object> position = positionOf(owner, "APXR11");
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("10");
        assertThat(number(position.get("totalInvested"))).isEqualByComparingTo("90.00");
    }
}
