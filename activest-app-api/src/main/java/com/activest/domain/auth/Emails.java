package com.activest.domain.auth;

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
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String name = email.substring(0, at);
        String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return visible + "*".repeat(Math.max(3, name.length() - visible.length())) + email.substring(at);
    }
}
