package com.activest.domain.issuer;

/** Classe da acao (V28). UNIT e um pacote de outras classes (ver UnitComposition). */
public enum ShareClass {
    ON, PN, PNA, PNB, PNC, PND, UNIT;

    /**
     * Classe pelo numero do ticker: 3 ON, 4 PN, 5 PNA, 6 PNB, 7 PNC, 8 PND, 11 UNIT.
     * O sufixo F (fracionario) e ignorado. Nulo quando o numero nao e de acao.
     */
    public static ShareClass fromTicker(String ticker) {
        if (ticker == null) {
            return null;
        }
        String t = ticker.trim().toUpperCase();
        if (t.endsWith("F")) {
            t = t.substring(0, t.length() - 1);
        }
        String digits = t.replaceAll("^[A-Z0-9]{4}", "");
        return switch (digits) {
            case "3" -> ON;
            case "4" -> PN;
            case "5" -> PNA;
            case "6" -> PNB;
            case "7" -> PNC;
            case "8" -> PND;
            case "11" -> UNIT;
            default -> null;
        };
    }
}
