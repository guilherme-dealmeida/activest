package com.activest.domain.goal.type;

/** Como o tipo trata a reserva de emergencia (D7). */
public enum ReservationRule {
    /** Patrimonio: escolha explicita no cadastro, sem pre-marcacao (D7, 13/10/2026). */
    EXPLICIT,
    /** Alocacao e concentracao: padrao sem a reserva, escolha por meta. */
    DEFAULT_WITHOUT,
    /** Demais tipos com escopo: padrao com a reserva (carteira inteira), escolha por meta. */
    DEFAULT_WITH,
    /** O tipo nao tem escopo de carteira (ativo unico, estrategia). */
    NONE
}
