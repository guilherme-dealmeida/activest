package com.primevest.domain.catalog;

import java.util.Set;

/**
 * "O catalogo mudou para estes ativos" — a adm-api avisa, a app-api recalcula as posicoes
 * (PLANO_ADM_API.md, 10.3). A adm-api grava so catalogo; o que muda na carteira de cada
 * usuario e a app-api quem grava.
 *
 * @param origin   PROVENTOS: eventos em dinheiro ou societarios novos/completados — refaz
 *                 proventos e valorizacao; FECHAMENTO: preco novo — refaz so a valorizacao
 * @param assetIds ativos cujas posicoes devem ser recalculadas
 */
public record CatalogChange(Origin origin, Set<Long> assetIds) {

    public enum Origin { PROVENTOS, FECHAMENTO }

    public CatalogChange {
        assetIds = assetIds == null ? Set.of() : Set.copyOf(assetIds);
    }
}
