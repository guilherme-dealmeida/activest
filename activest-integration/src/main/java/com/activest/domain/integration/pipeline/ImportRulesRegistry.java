package com.activest.domain.integration.pipeline;

import com.activest.domain.integration.IntegrationProvider;
import com.activest.domain.integration.source.ImportedMovement;
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

    private final Map<IntegrationProvider, SourceConnector> connectors = new EnumMap<>(IntegrationProvider.class);

    public ImportRulesRegistry(List<SourceConnector> connectors) {
        connectors.forEach(c -> this.connectors.put(c.provider(), c));
    }

    /** As regras principais da fonte. */
    public Optional<ImportRules> forProvider(IntegrationProvider provider) {
        return Optional.ofNullable(connectors.get(provider)).map(SourceConnector::rules);
    }

    /**
     * As regras que valem para um movimento gravado (confirmar, restaurar — E4): a fonte pelo prefixo do
     * externalId, e dentro dela a regra do tipo do movimento (docs/planos/PLANO_INTEGRACOES.md, 10.2).
     */
    public ImportRules forMovement(ImportedMovement movement) {
        IntegrationProvider provider = IntegrationProvider.fromExternalId(movement.externalId());
        SourceConnector c = connectors.get(provider);
        if (c == null) {
            throw new IllegalStateException("Fonte sem conector registrado: " + provider);
        }
        return c.rulesFor(movement);
    }
}
