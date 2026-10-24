package com.activest.domain.integration.pipeline;

import com.activest.domain.integration.IntegrationProvider;
import com.activest.domain.integration.source.SourceConnector;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * As regras de importacao de cada fonte, pelos conectores registrados
 * (docs/planos/PLANO_INTEGRACAO_B3.md, G3). Usado onde so se tem o movimento
 * gravado — confirmar, restaurar (E4) — e e preciso reaplicar as regras da
 * fonte de onde ele veio.
 */
@Component
public class ImportRulesRegistry {

    private final Map<IntegrationProvider, ImportRules> rules = new EnumMap<>(IntegrationProvider.class);

    public ImportRulesRegistry(List<SourceConnector> connectors) {
        connectors.forEach(c -> rules.put(c.provider(), c.rules()));
    }

    public Optional<ImportRules> forProvider(IntegrationProvider provider) {
        return Optional.ofNullable(rules.get(provider));
    }

    /** As regras da fonte de um movimento importado, pelo prefixo do externalId. */
    public ImportRules forExternalId(String externalId) {
        IntegrationProvider provider = IntegrationProvider.fromExternalId(externalId);
        return forProvider(provider).orElseThrow(() ->
            new IllegalStateException("Fonte sem conector registrado: " + provider));
    }
}
