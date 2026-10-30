package com.activest.adm.domain.admin;

import com.activest.adm.domain.dividend.CvmPaymentDateService;
import com.activest.adm.domain.dividend.PaymentDateSurveyService;
import com.activest.adm.domain.dividend.DividendCatalogService;
import com.activest.adm.domain.fundamentals.FiiMonthlyImportService;
import com.activest.adm.domain.fundamentals.FinancialStatementImportService;
import com.activest.adm.domain.issuer.IssuerImportService;
import com.activest.adm.domain.marketindex.MarketIndexImportService;
import com.activest.adm.domain.pricehistory.TreasuryPriceService;
import com.activest.adm.domain.pricehistory.HistoricalQuotesImportService;
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
    private final DividendCatalogService dividends = mock(DividendCatalogService.class);
    private final FinancialStatementImportService statements = mock(FinancialStatementImportService.class);
    private final FiiMonthlyImportService fii = mock(FiiMonthlyImportService.class);
    private final CvmPaymentDateService dates = mock(CvmPaymentDateService.class);
    private final PaymentDateSurveyService survey = mock(PaymentDateSurveyService.class);
    private final MarketIndexImportService indices = mock(MarketIndexImportService.class);
    private final TreasuryPriceService treasury = mock(TreasuryPriceService.class);
    private final com.activest.adm.domain.fixedincome.FixedIncomeIssuerImportService fiIssuers =
            mock(com.activest.adm.domain.fixedincome.FixedIncomeIssuerImportService.class);
    private final BootstrapService service = new BootstrapService(issuers, quotes, dividends, statements, fii, dates,
            survey, indices, treasury, fiIssuers,
            Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    @DisplayName("Ordem: emissores, emissoras de renda fixa, COTAHIST (ISIN), proventos (eventos societarios), datas de pagamento (CVM e levantamento), balancos (escala EVENTO), informe de FII, indices, Tesouro")
    void order() {
        when(dividends.syncAll(anyString())).thenReturn(new DividendCatalogService.SyncResult(654, 0, 0, 0));

        BootstrapService.Result r = service.run("teste");

        InOrder o = inOrder(issuers, fiIssuers, quotes, dividends, dates, survey, statements, fii, indices,
                treasury);
        o.verify(issuers).importAll(anyString());
        o.verify(fiIssuers).importAll(anyString());
        o.verify(quotes).importYears(eq(2010), eq(2026), anyString());
        o.verify(dividends).syncAll(anyString());
        o.verify(dates).run(eq(2022), eq(2026), anyString());
        o.verify(survey).run(anyString());
        o.verify(statements).importYears(eq(2010), eq(2026), eq(false), anyString());
        o.verify(fii).importYears(eq(2016), eq(2026), eq(false), anyString());
        o.verify(indices).importAll(anyString());
        o.verify(treasury).importFrom(eq(2010), anyString());
        assertThat(r.steps()).hasSize(10);
    }

    @Test
    @DisplayName("Exterior por último (PLANO_ATIVOS_INTERNACIONAIS, F7): o catálogo vivo e os deslistados, gravando")
    void foreign() {
        when(dividends.syncAll(anyString())).thenReturn(new DividendCatalogService.SyncResult(654, 0, 0, 0));
        service.foreignCatalog = mock(com.activest.adm.domain.foreign.ForeignCatalogService.class);
        service.foreignDelisted = mock(com.activest.adm.domain.foreign.ForeignDelistedService.class);

        BootstrapService.Result r = service.run("teste");

        InOrder o = inOrder(treasury, service.foreignCatalog, service.foreignDelisted);
        o.verify(treasury).importFrom(eq(2010), anyString());
        o.verify(service.foreignCatalog).run(eq(true), anyString(), org.mockito.ArgumentMatchers.any());
        o.verify(service.foreignDelisted).run(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), eq(true), anyString(), org.mockito.ArgumentMatchers.any());
        assertThat(r.steps()).hasSize(12);
    }

    @Test
    @DisplayName("Etapa que falha fica registrada e as seguintes rodam; o resultado sai com falha")
    void failureDoesNotStop() {
        when(quotes.importYears(eq(2010), eq(2026), anyString())).thenThrow(new IllegalStateException("B3 fora"));
        when(dividends.syncAll(anyString())).thenReturn(new DividendCatalogService.SyncResult(654, 0, 0, 0));

        BootstrapService.Result r = service.run("teste");

        assertThat(r.ok()).isFalse();
        assertThat(r.steps().get(2).ok()).isFalse();
        assertThat(r.steps().get(2).summary()).isEqualTo("B3 fora");
        assertThat(r.steps().get(3).ok()).isTrue();
        org.mockito.Mockito.verify(fii).importYears(eq(2016), eq(2026), eq(false), anyString());
    }
}
