package com.primevest.domain.auth;

import com.primevest.exception.BusinessException;

/**
 * Celular em E.164 (D6): {@code +5541999999999}. A tela manda com máscara; 10 ou 11 dígitos são
 * número brasileiro com DDD e ganham o +55; com {@code +} na frente, vale como veio (8 a 15 dígitos).
 */
public final class Phones {

    private Phones() {
    }

    /** Nulo ou em branco = sem celular. */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (phone.trim().startsWith("+")) {
            if (digits.length() < 8 || digits.length() > 15) {
                throw new BusinessException("Informe um celular válido, com DDD.");
            }
            return "+" + digits;
        }
        if (digits.length() == 10 || digits.length() == 11) {
            return "+55" + digits;
        }
        if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith("55")) {
            return "+" + digits;
        }
        throw new BusinessException("Informe um celular válido, com DDD.");
    }
}
