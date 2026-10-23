package com.activest.domain.fixedincome;

/**
 * Fundo garantidor da empresa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R11). So informacao: o limite por
 * conglomerado esta fora do escopo.
 */
public enum Guarantor {
    /** Fundo Garantidor de Creditos: bancos e financeiras. */
    FGC,
    /** Fundo Garantidor do Cooperativismo de Credito: cooperativas e bancos cooperativos. */
    FGCOOP,
    NENHUM
}
