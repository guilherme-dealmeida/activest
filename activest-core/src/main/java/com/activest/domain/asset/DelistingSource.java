package com.activest.domain.asset;

/**
 * Prova da inativação de um papel ({@code assets.delisting_source}, V14 — docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, I5
 * e C7). Valores de registro, como no banco (REGRAS 9.2).
 */
public enum DelistingSource {
    /** Registro da companhia cancelado na CVM (menos migração de registro). */
    CVM,
    /** Form 25 / 25-NSE da SEC: a bolsa comunicou a retirada (exterior). */
    FORM_25,
    /** O ticker sumiu da lista oficial e está sem negócio há 30 pregões (sem ser renomeação). */
    LIST_GONE,
    /** Decisão do administrador (inativar ou desfazer): a conferência nunca a refaz. */
    MANUAL
}
