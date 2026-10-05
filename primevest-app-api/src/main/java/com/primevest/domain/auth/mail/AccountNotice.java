package com.primevest.domain.auth.mail;

/** Avisos por e-mail que não levam código. */
public enum AccountNotice {
    /** Cadastro pedido com um e-mail que já tem conta (D10: a tela não diz; o e-mail diz). */
    ACCOUNT_EXISTS,
    PASSWORD_CHANGED,
    /** Vai para o endereço ANTIGO, depois da troca. */
    EMAIL_CHANGED,
    ACCOUNT_DELETED
}
