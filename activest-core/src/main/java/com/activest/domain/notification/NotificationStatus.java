package com.activest.domain.notification;

/**
 * Situação do assunto de uma pendência (R6). Mensagem informativa não tem status (nulo). Independente do estado de
 * leitura/arquivo/exclusão (R1).
 */
public enum NotificationStatus {
    NAO_RESOLVIDA,
    /** A pessoa decidiu não tratar; pode reabrir. */
    DISPENSADA,
    /** O sistema viu que o assunto deixou de existir. */
    RESOLVIDA
}
