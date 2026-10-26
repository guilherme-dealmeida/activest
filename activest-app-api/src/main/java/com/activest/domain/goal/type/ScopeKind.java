package com.activest.domain.goal.type;

/**
 * Formas de escopo e dimensoes de alocacao/concentracao (docs/planos/PLANO_METAS.md, Anexo A 3). Regiao e moeda
 * ficam fora (D16: escopo proprio de outras moedas).
 */
public enum ScopeKind {
    ALL("Carteira total"),
    TYPE("Tipo de ativo"),
    CATEGORY("Categoria"),
    INDEXER("Indexador"),
    ISSUER("Emissor"),
    ASSET("Ativo"),
    POSITIONS("Posições escolhidas");

    private final String label;

    ScopeKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
