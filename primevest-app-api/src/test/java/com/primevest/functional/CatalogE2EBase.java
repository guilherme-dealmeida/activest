package com.primevest.functional;

import com.primevest.adm.domain.dividend.DividendCatalogService;
import com.primevest.adm.domain.pricehistory.ClosingPriceService;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3Supplement;
import com.primevest.adm.integration.b3.B3SupplementStore;
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
 * Testes de ponta a ponta do catalogo para a carteira (docs/planos/PLANO_ADM_API.md, 10.4): fonte
 * mockada (B3, FNET, BRAPI, CoinGecko) → rotina da adm-api grava o catalogo → "catalogo
 * mudou" → a carteira recalcula → a API do usuario mostra.
 *
 * <p>A adm-api esta no classpath so dos testes e a app-api nao a varre; aqui entram so as
 * duas rotinas usadas ({@link DividendCatalogService}, {@link ClosingPriceService}), e a
 * mensagem, que em producao passa pelo Redis, chama o {@link PositionRefreshService} na hora
 * — o teste le a carteira logo depois da rotina, como antes da separacao.</p>
 */
@Import(CatalogE2EBase.Catalog.class)
public abstract class CatalogE2EBase extends FunctionalBase {

    @MockBean protected B3Client b3Client;
    @MockBean protected FnetClient fnetClient;
    @MockBean protected BrApiClient brApiClient;
    @MockBean protected CoinGeckoClient coinGeckoClient;

    @TestConfiguration(proxyBeanMethods = false)
    @Import({DividendCatalogService.class, ClosingPriceService.class,
        com.primevest.adm.domain.notice.AdminNotices.class})
    static class Catalog {

        /**
         * Os 12 meses da B3 sem disco (docs/planos/PLANO_CARGA_INICIAL.md, P1): cada teste ve so a resposta
         * mockada, sem o acumulado de outro teste nem a carga/data/ real do projeto.
         */
        @Bean
        B3SupplementStore b3SupplementStoreWithoutDisk(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
            return new B3SupplementStore(objectMapper, "target/it-data-app") {
                @Override
                public B3Supplement merge(boolean fund, String issuer, B3Supplement fresh) {
                    return fresh;
                }
            };
        }

        /** "Catalogo mudou" sem Redis: a carteira recalcula na hora. */
        @Bean
        @Primary
        CatalogChangePublisher directCatalog(PositionRefreshService positionRefreshService) {
            return positionRefreshService::refresh;
        }
    }
}
