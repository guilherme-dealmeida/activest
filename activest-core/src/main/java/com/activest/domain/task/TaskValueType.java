package com.activest.domain.task;

/** Tipo do valor de um atributo de tarefa: a tela escolhe o campo por ele e {@link TaskAttributeValues} valida. */
public enum TaskValueType {
    /** Ano com 4 dígitos. */
    YEAR,
    /** Data ISO (aaaa-mm-dd). */
    DATE,
    /** {@code true} ou {@code false}. */
    BOOLEAN,
    /** Número inteiro. */
    INTEGER,
    /** Texto livre (pode ter várias linhas). */
    TEXT,
    /** Itens separados por vírgula. */
    LIST
}
