package com.activest.domain.integration.pipeline;

import com.activest.domain.asset.Asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * O que as {@link ImportRules} podem consultar fora do movimento (docs/planos/PLANO_INTEGRACOES.md, 10.2): o catalogo,
 * nos tipos que a regra traz, e o cambio da data. As regras continuam puras — o contexto e montado por quem chama
 * ({@link ImportContextFactory} na aplicacao; {@link #catalogOnly} nos testes).
 */
public interface ImportContext {

    /** O ativo do catalogo pelo ticker, so nos tipos informados (V13: o ticker e unico por tipo). */
    Optional<Asset> asset(String ticker, Set<String> assetTypes);

    /**
     * Cambio da moeda para a moeda base na data: a PTAX venda, a mesma que o lancamento usa quando o cambio nao e
     * informado (moedas, D5/D16; integracoes, D10). Vazio sem boletim.
     */
    Optional<BigDecimal> fxRate(String currencyCode, LocalDate date);

    /** So o catalogo, sem cambio (regras que nao precisam dele e testes). */
    static ImportContext catalogOnly(Function<String, Optional<Asset>> catalog) {
        return of(catalog, (currency, date) -> Optional.empty());
    }

    static ImportContext of(Function<String, Optional<Asset>> catalog,
                            java.util.function.BiFunction<String, LocalDate, Optional<BigDecimal>> fx) {
        return new ImportContext() {
            @Override
            public Optional<Asset> asset(String ticker, Set<String> assetTypes) {
                return catalog.apply(ticker);
            }

            @Override
            public Optional<BigDecimal> fxRate(String currencyCode, LocalDate date) {
                return fx.apply(currencyCode, date);
            }
        };
    }
}
