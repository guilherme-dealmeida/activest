package com.activest.domain.goal;

/**
 * Natureza da meta (docs/planos/PLANO_METAS.md, Anexo A 7.1): decide como a situacao e calculada e se ha "% de
 * progresso" (so na crescente e na sequencia — G5: limite e faixa mostram a posicao em relacao ao limite).
 */
public enum GoalNature {
    /** Chegar a um valor (patrimonio, renda, cotas): progresso, falta, conclusao automatica (D13). */
    CRESCENTE,
    /** Ficar acima de um minimo ou abaixo de um maximo, ou dentro de uma faixa: sem progresso, sem conclusao. */
    CONTROLE,
    /** Cumprir um valor por periodo (aporte mensal): periodos cumpridos e sequencia. */
    SEQUENCIA,
    /** Estimativa de longo prazo (aposentadoria): nunca "atingida" antes da data. */
    ESTIMATIVA
}
