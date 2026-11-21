package com.activest.domain.asset;

/**
 * Regra do ticker base: o fracionario e o mesmo papel (PETR4F -> PETR4).
 *
 * <p>E a unica fonte desta regra no projeto (docs/planos/PLANO_TICKER_BASE.md, D1 e D2). Antes ela estava escrita
 * em 10 lugares, em 3 variantes que discordavam entre si: 5 exigiam digito antes do F (e quebravam a classe B,
 * MRSA3BF), 4 nao exigiam (e quebravam os tickers com hifen, TRTN-F) e 1 usava regex (quebravam os dois).
 *
 * <p>O F final e o marcador do fracionario quando o ticker tem 6 ou mais caracteres, nao tem hifen e o F vem depois
 * de letra ou digito. O hifen e o que separa o ticker de papel inteiro com sufixo (TRTN-F, BANC-F, RITM-F) do
 * fracionario: nenhum deles tem lote padrao no catalogo, e nenhum fracionario do catalogo tem base com hifen.
 */
public final class Tickers {

    /** O ticker do papel inteiro a partir do fracionario: PETR4F -> PETR4, MRSA3BF -> MRSA3B. */
    public static String base(String ticker) {
        if (ticker == null) {
            return null;
        }
        String t = ticker.trim().toUpperCase();
        return isOddLot(t) ? t.substring(0, t.length() - 1) : t;
    }

    /** O ticker e fracionario (o F final e o marcador): PETR4F -> true, PETR4 -> false, TRTN-F -> false. */
    public static boolean isOddLot(String ticker) {
        if (ticker == null) {
            return false;
        }
        String t = ticker.trim().toUpperCase();
        return t.length() > 5 && t.endsWith("F") && t.indexOf('-') < 0
                && Character.isLetterOrDigit(t.charAt(t.length() - 2));
    }

    private Tickers() {
    }
}
