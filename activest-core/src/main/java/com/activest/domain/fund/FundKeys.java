package com.activest.domain.fund;

import java.text.Normalizer;
import java.util.Locale;

/** CNPJ e texto de busca dos fundos (PLANO_FUNDOS_INVESTIMENTO.md, D3 e D25). Regra pura. */
public final class FundKeys {

    private FundKeys() {
    }

    /**
     * CNPJ sem pontuação e em maiúsculas: "03.926.431/0001-71" → "03926431000171". Aceita o CNPJ alfanumérico
     * (letras nas 12 primeiras posições, desde 07/2026). Devolve nulo se não sobrarem 14 caracteres.
     */
    public static String cnpj(String raw) {
        if (raw == null) {
            return null;
        }
        // Sem regex: roda nas ~71 milhões de linhas do informe a cada varredura completa
        char[] out = new char[14];
        int n = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean upper = c >= 'A' && c <= 'Z';
            boolean lower = c >= 'a' && c <= 'z';
            if (!digit && !upper && !lower) {
                continue;
            }
            if (n == 14) {
                return null;
            }
            out[n++] = lower ? (char) (c - 32) : c;
        }
        return n == 14 ? new String(out) : null;
    }

    /** CNPJ com a máscara: "03926431000171" → "03.926.431/0001-71". */
    public static String format(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj;
        }
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8) + "/"
                + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }

    /** Sem acento, maiúsculas e espaços simples: a forma comparada na busca ("Previdência" acha "PREVIDENCIA"). */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
