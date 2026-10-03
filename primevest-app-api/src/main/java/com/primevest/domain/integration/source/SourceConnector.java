package com.primevest.domain.integration.source;

import com.primevest.domain.integration.IntegrationProvider;
import com.primevest.domain.integration.credential.ApiKeyCredentials;

import java.util.Map;

/**
 * O contrato de toda fonte externa (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.2).
 *
 * <p>Cada corretora e uma implementacao; a Binance e a primeira (F3). O
 * conector so sabe falar com a sua fonte: autentica, pagina, respeita os
 * limites de peso, agrupa os pedacos de uma ordem (R1) e monta o
 * {@link ImportedMovement} com um {@code externalId} estavel (R5). Nenhuma
 * regra de negocio do PrimeVest mora aqui — elas ficam no ImportPipeline.</p>
 */
public interface SourceConnector {

    IntegrationProvider provider();

    /**
     * Confere a chave na fonte: se e aceita e se e so de leitura. Chamado ao
     * conectar e antes de cada sincronizacao (P2).
     */
    CredentialCheck check(ApiKeyCredentials credentials);

    /**
     * Busca o que houve desde os cursores informados (vazio = historico inteiro).
     */
    FetchResult fetch(ApiKeyCredentials credentials, Map<String, String> cursors);
}
