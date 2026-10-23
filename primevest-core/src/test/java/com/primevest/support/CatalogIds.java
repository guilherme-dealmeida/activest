package com.primevest.support;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.AssetTypeRepository;

/**
 * Resolve catalogo por NOME e TICKER, nunca por id.
 *
 * <p>asset_types e assets sao BIGSERIAL semeados por migration: o id depende da
 * ordem de execucao e fixa-lo no teste e a mesma armadilha que a documentacao
 * do projeto proibe no codigo.</p>
 */
public final class CatalogIds {

    /** Tickers usados nos testes; todos existem nos seeds V4–V7. */
    public static final String STOCK = "PETR4";
    public static final String FII = "HGLG11";
    public static final String TREASURY_IPCA_2050 = "Tesouro IPCA+ 2050";
    public static final String CDB = "CDB";
    /** Cripto do seed V19. */
    public static final String BTC = "BTC";

    private CatalogIds() {
    }

    public static AssetType type(AssetTypeRepository repository, String name) {
        return repository.findAll().stream()
            .filter(t -> t.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Tipo de ativo ausente no seed: " + name));
    }

    public static Asset asset(AssetRepository repository, String ticker) {
        return repository.findByTicker(ticker)
            .orElseThrow(() -> new IllegalStateException("Ativo ausente no seed: " + ticker));
    }
}
