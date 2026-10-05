package com.primevest.domain.auth;

import java.util.Locale;

/**
 * E-mail é o login: gravado e procurado sempre normalizado (G6). Até a V41 {@code Guilherme@…} e
 * {@code guilherme@…} eram duas contas.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** "gu*******@gmail.com": para a tela dizer para onde o código foi sem expor o endereço inteiro. */
    public static String mask(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        String nome = email.substring(0, arroba);
        String visivel = nome.length() <= 2 ? nome.substring(0, 1) : nome.substring(0, 2);
        return visivel + "*".repeat(Math.max(3, nome.length() - visivel.length())) + email.substring(arroba);
    }
}
