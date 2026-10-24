package com.activest.domain.catalog;

/**
 * Contrato das mensagens entre a adm-api e a app-api (docs/planos/PLANO_ADM_API.md, 10.3). Redis pub/sub:
 * sem garantia de entrega — a app-api recalcula todas as posicoes uma vez por dia, e isso cobre
 * mensagem perdida.
 *
 * <p>{@code activest.messaging.enabled=false} (testes, e quem roda sem Redis) troca as
 * implementacoes Redis pelas de {@link MessagingFallbackConfig}, que so registram no log.</p>
 */
public final class CatalogMessaging {

    /** adm → app: {@link CatalogChange} em JSON. */
    public static final String CATALOG_CHANNEL = "activest:catalog:assets";

    /** app → adm: id do ativo cujo historico de proventos a carteira precisa. */
    public static final String ON_DEMAND_CHANNEL = "activest:dividends:on-demand";

    public static final String ENABLED_PROPERTY = "activest.messaging.enabled";

    private CatalogMessaging() {
    }

    /** Publica "o catalogo mudou". Implementado na adm-api. */
    public interface CatalogChangePublisher {
        void publish(CatalogChange change);
    }

    /** Pede o historico de proventos de um ativo. Implementado na app-api. */
    public interface OnDemandRequestPublisher {
        void requestHistory(Long assetId);
    }
}
