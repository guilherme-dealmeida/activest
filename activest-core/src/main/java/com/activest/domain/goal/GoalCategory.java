package com.activest.domain.goal;

/** As 8 categorias do catalogo de metas (docs/planos/PLANO_METAS.md, Anexo A 4). */
public enum GoalCategory {
    PATRIMONIO("Patrimônio e acumulação"),
    APORTES("Aportes e disciplina"),
    RENDA("Renda passiva"),
    RENTABILIDADE("Rentabilidade e desempenho"),
    ALOCACAO("Alocação e diversificação"),
    RISCO("Risco e concentração"),
    ATIVO("Metas por ativo"),
    LONGO_PRAZO("Objetivos de longo prazo");

    private final String label;

    GoalCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
