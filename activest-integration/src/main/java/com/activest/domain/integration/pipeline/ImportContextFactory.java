package com.activest.domain.integration.pipeline;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetResolver;
import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.RateSide;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Monta o {@link ImportContext} da aplicacao (docs/planos/PLANO_INTEGRACOES.md, 10.2): catalogo pelo
 * {@link AssetResolver} e cambio pela PTAX venda do {@link ExchangeRateService}. Cada contexto guarda o que ja
 * consultou — use um por pre-visualizacao ou confirmacao.
 */
@Component
public class ImportContextFactory {

    private final AssetResolver assets;
    private final ExchangeRateService rates;

    public ImportContextFactory(AssetResolver assets, ExchangeRateService rates) {
        this.assets = assets;
        this.rates = rates;
    }

    public ImportContext create() {
        Map<String, Optional<Asset>> catalog = new HashMap<>();
        Map<String, Optional<BigDecimal>> fx = new HashMap<>();
        return new ImportContext() {
            @Override
            public Optional<Asset> asset(String ticker, Set<String> assetTypes) {
                return catalog.computeIfAbsent(ticker + "|" + String.join(",", new java.util.TreeSet<>(assetTypes)),
                    k -> assets.findInTypes(ticker, assetTypes));
            }

            @Override
            public Optional<BigDecimal> fxRate(String currencyCode, LocalDate date) {
                if (rates.isBase(currencyCode)) {
                    return Optional.of(BigDecimal.ONE);
                }
                return fx.computeIfAbsent(currencyCode + "|" + date, k -> rates.rateOn(currencyCode, date, RateSide.SELL)
                    .map(ExchangeRateService.Quote::rate));
            }
        };
    }
}
