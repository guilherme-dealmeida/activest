package com.primevest.domain.integration.dto;

import com.primevest.domain.integration.IntegrationProvider;

/**
 * Uma fonte ligada no servidor, para a tela de Integracoes oferecer (docs/planos/PLANO_INTEGRACAO_B3.md, F6).
 *
 * @param credential o que a fonte pede para conectar: {@code API_KEY} (chave e segredo) ou {@code DOCUMENT} (CPF)
 * @param oncePerDay a fonte aceita uma consulta por dia para o mesmo investidor (B3)
 */
public record SourceDTO(IntegrationProvider provider, String credential, boolean oncePerDay) {}
