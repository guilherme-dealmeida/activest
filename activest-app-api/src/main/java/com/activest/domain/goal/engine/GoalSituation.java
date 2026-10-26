package com.activest.domain.goal.engine;

/** Situacao da meta ativa, calculada a cada avaliacao (docs/planos/PLANO_METAS.md, Anexo A 7.1). */
public enum GoalSituation {
    NO_RITMO("No ritmo"),
    EM_ANDAMENTO("Em andamento"),
    ATRASADA("Atrasada"),
    PRAZO_VENCIDO("Prazo vencido"),
    DENTRO_DO_OBJETIVO("Dentro do objetivo"),
    FORA_DO_OBJETIVO("Fora do objetivo"),
    ATINGIDA("Atingida"),
    ESTIMATIVA("Estimativa"),
    SEM_DADOS("Sem dados suficientes");

    private final String label;

    GoalSituation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
