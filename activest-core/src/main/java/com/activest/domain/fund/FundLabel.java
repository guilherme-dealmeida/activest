package com.activest.domain.fund;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Nome curto do fundo para as telas (PLANO_FUNDOS_INVESTIMENTO.md, D3 e C1): a denominação comercial vigente, ou a
 * social atual quando a CVM não tem a comercial, abreviada por uma tabela fixa — sem curadoria e sem fonte nova.
 *
 * <p>Ex.: "CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA" →
 * "CAIXA 300 PREV FIC CLASSE FIF RF RL". Sem corte de tamanho: a tela usa reticências. Regra pura.</p>
 */
public final class FundLabel {

    /** Na ordem: as expressões longas antes das curtas que elas contêm. */
    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();

    static {
        rule("FUNDOS? DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO", "FIC FI");
        rule("FUNDOS? DE INVESTIMENTO EM COTAS", "FIC");
        rule("FUNDOS? DE INVESTIMENTO FINANCEIRO", "FIF");
        rule("FUNDOS? DE INVESTIMENTO", "FI");
        rule("CLASSE DE INVESTIMENTO EM COTAS", "CIC");
        rule("CLASSE DE", "CLASSE");
        rule("DE (FIF|FIC|FI|CLASSE)", "$1");
        rule("RESPONSABILIDADE LIMITADA", "RL");
        rule("RENDA FIXA", "RF");
        rule("MULTIMERCADOS?", "MULT");
        rule("CR[EÉ]DITO PRIVADO", "CP");
        rule("INVESTIMENTO (NO )?EXTERIOR", "IE");
        rule("PREVIDENCI[AÁ]RIO", "PREV");
        rule("LONGO PRAZO", "LP");
        rule("REFERENCIADO", "REF");
    }

    private static void rule(String regex, String replacement) {
        RULES.put(Pattern.compile("(?<![\\p{L}\\p{N}])" + regex + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE
                | Pattern.UNICODE_CASE), replacement);
    }

    private FundLabel() {
    }

    /** O rótulo: base = denominação comercial (se houver), senão a social. */
    public static String of(String tradeName, String legalName) {
        String base = tradeName != null && !tradeName.isBlank() ? tradeName : legalName;
        return abbreviate(base);
    }

    public static String abbreviate(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim().replaceAll("\\s+", " ");
        for (Map.Entry<Pattern, String> r : RULES.entrySet()) {
            s = r.getKey().matcher(s).replaceAll(r.getValue());
        }
        // hífen solto vira espaço ("RF - RL" → "RF RL"); hífen dentro de palavra fica ("L/S - DIRECIONAL")
        s = s.replaceAll("\\s+-\\s+", " ").replaceAll("\\s+-$", "").replaceAll("^-\\s+", "");
        return s.replaceAll("\\s+", " ").trim();
    }
}
