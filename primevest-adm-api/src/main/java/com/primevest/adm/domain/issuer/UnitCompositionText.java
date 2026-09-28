package com.primevest.adm.domain.issuer;

import com.primevest.domain.issuer.ShareClass;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Composicao da unit a partir do texto livre do FCA ({@code Composicao_BDR_Unit}).
 * Formatos vistos em 2024–2026: "1 KLBN3 + 4 KLBN4", "1 ON / 2 PN", "1 ON e 2 PN",
 * "1 ON+ 2PN", "1 ON E 2 PNA", "2 ações preferenciais e 1 ação ordinária",
 * "1 ação ordinária e 4 ações preferenciais".
 *
 * <p>Itens que nao sao acao (recibo e bonus de subscricao) sao ignorados. Texto
 * que nao da nenhuma classe devolve vazio — a unit vai para a conferencia manual.</p>
 */
public final class UnitCompositionText {

    private static final Pattern ITEM = Pattern.compile(
            "(\\d+)\\s*(acoes preferenciais|acao preferencial|acoes ordinarias|acao ordinaria"
                    + "|[a-z]{4}\\d{1,2}|pn[a-d]?s?|on)\\b");

    private UnitCompositionText() {
    }

    public static Map<ShareClass, Integer> parse(String text) {
        Map<ShareClass, Integer> out = new EnumMap<>(ShareClass.class);
        if (text == null || text.isBlank()) {
            return out;
        }
        String norm = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        Matcher m = ITEM.matcher(norm);
        while (m.find()) {
            int qty = Integer.parseInt(m.group(1));
            ShareClass c = classOf(m.group(2));
            if (c != null && c != ShareClass.UNIT && qty > 0) {
                out.merge(c, qty, Integer::sum);
            }
        }
        return out;
    }

    private static ShareClass classOf(String token) {
        if (token.startsWith("acao ordinaria") || token.startsWith("acoes ordinarias") || token.equals("on")) {
            return ShareClass.ON;
        }
        if (token.startsWith("acao preferencial") || token.startsWith("acoes preferenciais")) {
            return ShareClass.PN;
        }
        if (token.startsWith("pn")) {
            String t = token.endsWith("s") ? token.substring(0, token.length() - 1) : token;
            return switch (t) {
                case "pna" -> ShareClass.PNA;
                case "pnb" -> ShareClass.PNB;
                case "pnc" -> ShareClass.PNC;
                case "pnd" -> ShareClass.PND;
                default -> ShareClass.PN;
            };
        }
        return ShareClass.fromTicker(token.toUpperCase());
    }
}
