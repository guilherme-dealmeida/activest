package com.primevest.functional;

import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.adm.domain.pricehistory.ClosingPriceService;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.CvmFundRegistry;
import com.primevest.adm.integration.b3.FnetClient;
import com.primevest.adm.integration.brapi.BrApiClient;
import com.primevest.adm.integration.coingecko.CoinGeckoClient;
import com.primevest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.primevest.domain.portfolio.PositionRefreshService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Testes de ponta a ponta do catalogo para a carteira (PLANO_ADM_API.md, 10.4): fonte
 * mockada (B3, FNET, BRAPI, CoinGecko) → rotina da adm-api grava o catalogo → "catalogo
 * mudou" → a carteira recalcula → a API do usuario mostra.
 *
 * <p>A adm-api esta no classpath so dos testes e a app-api nao a varre; aqui entram so as
 * duas rotinas usadas ({@link ProventosCatalogService}, {@link ClosingPriceService}), e a
 * mensagem, que em producao passa pelo Redis, chama o {@link PositionRefreshService} na hora
 * — o teste le a carteira logo depois da rotina, como antes da separacao.</p>
 */
@Import(CatalogoE2EBase.Catalogo.class)
public abstract class CatalogoE2EBase extends FuncionalBase {

    @MockBean protected B3Client b3Client;
    @MockBean protected CvmFundRegistry cvmFundRegistry;
    @MockBean protected FnetClient fnetClient;
    @MockBean protected BrApiClient brApiClient;
    @MockBean protected CoinGeckoClient coinGeckoClient;

    @TestConfiguration(proxyBeanMethods = false)
    @Import({ProventosCatalogService.class, ClosingPriceService.class})
    static class Catalogo {

        /** "Catalogo mudou" sem Redis: a carteira recalcula na hora. */
        @Bean
        @Primary
        CatalogChangePublisher catalogoDireto(PositionRefreshService positionRefreshService) {
            return positionRefreshService::refresh;
        }
    }
}
