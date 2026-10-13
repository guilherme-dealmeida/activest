package com.primevest.domain.catalog;

import java.util.Set;

/**
 * "O catalogo mudou para estes ativos" — a adm-api avisa, a app-api recalcula as posicoes
 * (docs/planos/PLANO_ADM_API.md, 10.3). A adm-api grava so catalogo; o que muda na carteira de cada
 * usuario e a app-api quem grava.
 *
 * @param origin     PROVENTOS: eventos em dinheiro ou societarios novos/completados — refaz
 *                   proventos e valorizacao; FECHAMENTO: preco novo (acoes, cripto, Tesouro) — refaz so a
 *                   valorizacao; INDICES: valor novo ou corrigido de indice de mercado — refaz a curva da renda fixa
 *                   privada (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R5); BALANCOS: trimestre novo (ou
 *                   reapresentado) da CVM — confere o "padrao mudou" do Preco Teto (docs/planos/PLANO_NOTIFICACOES.md,
 *                   5.6); nao mexe na carteira
 * @param assetIds   ativos cujas posicoes devem ser recalculadas (vazio em INDICES)
 * @param indexCodes indices que mudaram (so em INDICES)
 */
public record CatalogChange(Origin origin, Set<Long> assetIds, Set<String> indexCodes) {

    public enum Origin { PROVENTOS, FECHAMENTO, INDICES, BALANCOS }

    public CatalogChange {
        assetIds = assetIds == null ? Set.of() : Set.copyOf(assetIds);
        indexCodes = indexCodes == null ? Set.of() : Set.copyOf(indexCodes);
    }

    public CatalogChange(Origin origin, Set<Long> assetIds) {
        this(origin, assetIds, Set.of());
    }

    /** Indices de mercado que mudaram. */
    public static CatalogChange indices(Set<String> codes) {
        return new CatalogChange(Origin.INDICES, Set.of(), codes);
    }
}
