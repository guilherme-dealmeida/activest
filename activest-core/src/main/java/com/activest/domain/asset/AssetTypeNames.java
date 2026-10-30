package com.activest.domain.asset;

import java.util.Set;

/**
 * Nomes dos tipos de ativo ({@code asset_types.name}) que o código precisa citar. Valores de registro: ficam como
 * estão no banco (REGRAS 9.2). O id nunca é fixado — resolva sempre pelo nome.
 */
public final class AssetTypeNames {

    public static final String ACAO = "ACAO";
    public static final String FII = "FII";
    public static final String RENDA_FIXA = "RENDA_FIXA";
    public static final String TESOURO_DIRETO = "TESOURO_DIRETO";
    public static final String ETF = "ETF";
    public static final String FUNDO_INVESTIMENTO = "FUNDO_INVESTIMENTO";
    public static final String BDR = "BDR";
    public static final String STOCK = "STOCK";
    public static final String REIT = "REIT";
    public static final String ETF_INTERNACIONAL = "ETF_INTERNACIONAL";
    public static final String CRIPTO = "CRIPTO";
    public static final String OUTROS = "OUTROS";

    /** Renda variável negociada na B3: o que o COTAHIST, a BRAPI e a conferência do catálogo da B3 conhecem. */
    public static final Set<String> B3_LISTED = Set.of(ACAO, FII, ETF, BDR);

    /** Papéis listados no exterior (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md). */
    public static final Set<String> FOREIGN_LISTED = Set.of(STOCK, REIT, ETF_INTERNACIONAL);

    private AssetTypeNames() {
    }
}
