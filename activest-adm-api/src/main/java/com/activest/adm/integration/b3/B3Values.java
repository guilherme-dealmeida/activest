package com.activest.adm.integration.b3;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Conversao dos textos que o site da B3 devolve.
 *
 * <p>Numero no formato brasileiro ('0,47156696000', '1.234,56') e data em
 * dd/MM/yyyy. A B3 usa 31/12/9999 e 01/01/1900 como "sem data".</p>
 */
public final class B3Values {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private B3Values() {
    }

    public static BigDecimal decimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate date(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        try {
            LocalDate d = t.contains("-") ? LocalDate.parse(t.substring(0, 10)) : LocalDate.parse(t, BR);
            return d.getYear() <= 1900 || d.getYear() >= 9999 ? null : d;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String trim(String text) {
        return text == null ? null : text.trim();
    }
}
