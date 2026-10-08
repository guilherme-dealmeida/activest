package com.primevest.adm.domain.admin;

import com.primevest.adm.domain.dividend.CvmPaymentDateService;
import com.primevest.adm.domain.dividend.PaymentDateSurveyService;
import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.adm.domain.fundamentals.FiiMonthlyImportService;
import com.primevest.adm.domain.fundamentals.FinancialStatementImportService;
import com.primevest.adm.domain.issuer.IssuerImportService;
import com.primevest.adm.domain.marketindex.MarketIndexImportService;
import com.primevest.adm.domain.pricehistory.TreasuryPriceService;
import com.primevest.adm.domain.pricehistory.HistoricalQuotesImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** F11 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: a carga inicial roda as etapas em ordem e segue depois de uma falha. */
class BootstrapServiceTest {

    private final IssuerImportService issuers = mock(IssuerImportService.class);
    private final HistoricalQuotesImportService quotes = mock(HistoricalQuotesImportService.class);
    private final ProventosCatalogService proventos = mock(ProventosCatalogService.class);
    private final FinancialStatementImportService statements = mock(FinancialStatementImportService.class);
    private final FiiMonthlyImportService fii = mock(FiiMonthlyImportService.class);
    private final CvmPaymentDateService datas = mock(CvmPaymentDateService.class);
    private final PaymentDateSurveyService levantamento = mock(PaymentDateSurveyService.class);
    private final MarketIndexImportService indices = mock(MarketIndexImportService.class);
    private final TreasuryPriceService tesouro = mock(TreasuryPriceService.class);
    private final BootstrapService service = new BootstrapService(issuers, quotes, proventos, statements, fii, datas,
            levantamento, indices, tesouro,
            Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    @DisplayName("Ordem: emissores, COTAHIST (ISIN), proventos (eventos societarios), datas de pagamento (CVM e levantamento), balancos (escala EVENTO), informe de FII, indices, Tesouro")
    void ordem() {
        when(proventos.syncAll(anyString())).thenReturn(new ProventosCatalogService.SyncResult(654, 0, 0, 0));

        BootstrapService.Result r = service.run("teste");

        InOrder o = inOrder(issuers, quotes, proventos, datas, levantamento, statements, fii, indices, tesouro);
        o.verify(issuers).importAll(anyString());
        o.verify(quotes).importYears(eq(2010), eq(2026), anyString());
        o.verify(proventos).syncAll(anyString());
        o.verify(datas).run(eq(2022), eq(2026), anyString());
        o.verify(levantamento).run(anyString());
        o.verify(statements).importYears(eq(2010), eq(2026), eq(false), anyString());
        o.verify(fii).importYears(eq(2016), eq(2026), eq(false), anyString());
        o.verify(indices).importAll(anyString());
        o.verify(tesouro).importFrom(eq(2010), anyString());
        assertThat(r.steps()).hasSize(9);
    }

    @Test
    @DisplayName("Etapa que falha fica registrada e as seguintes rodam; o resultado sai com falha")
    void falhaNaoPara() {
        when(quotes.importYears(eq(2010), eq(2026), anyString())).thenThrow(new IllegalStateException("B3 fora"));
        when(proventos.syncAll(anyString())).thenReturn(new ProventosCatalogService.SyncResult(654, 0, 0, 0));

        BootstrapService.Result r = service.run("teste");

        assertThat(r.ok()).isFalse();
        assertThat(r.steps().get(1).ok()).isFalse();
        assertThat(r.steps().get(1).summary()).isEqualTo("B3 fora");
        assertThat(r.steps().get(2).ok()).isTrue();
        org.mockito.Mockito.verify(fii).importYears(eq(2016), eq(2026), eq(false), anyString());
    }
}
