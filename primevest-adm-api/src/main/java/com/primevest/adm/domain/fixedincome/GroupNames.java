package com.primevest.adm.domain.fixedincome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Nome automatico de um grupo a partir do nome do conglomerado no BCB (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md,
 * R9): tira o " - PRUDENCIAL" e as siglas societarias do fim ("SCFI", "DTVM", "S.A."...), troca "BCO" por "Banco"
 * e poe em maiusculas so o inicio das palavras — "BCO DO NORDESTE DO BRASIL S.A. - PRUDENCIAL" vira "Banco do
 * Nordeste do Brasil". E a mesma regra da sugestao "automatica" da planilha de curadoria de 09/10/2026; o nome
 * MANUAL (curado) nunca passa por aqui.
 */
public final class GroupNames {

    private static final Pattern PRUDENTIAL = Pattern.compile("\\s*-?\\s*PRUDENCIA\\w*\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAIL = Pattern.compile(
            "[\\s,/-]+(SCM|CCV|CTV|SCC|IP|SCD|SCFI|CFI|DTVM|CTVM|CCTVM|CCVM|CC|CI|SCMEPP|LTDA\\.?|S\\.?A\\.?|S/A|SA|BM|CH)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> LOWER = Set.of("DE", "DO", "DA", "DOS", "DAS", "E", "DI");
    private static final Set<String> NOT_ACRONYM = Set.of("PAY", "VIA", "EST", "BLU", "SUL", "LAR", "MAR", "SOL",
            "RIO", "NET", "ONE", "TOP", "BEM", "NEW", "BOA", "MAX", "BOM", "LUZ", "PIX", "APP", "FIT", "WAY", "KEY");

    private GroupNames() {
    }

    public static String automatic(String bcbName) {
        if (bcbName == null || bcbName.isBlank()) {
            return bcbName;
        }
        String s = PRUDENTIAL.matcher(bcbName.trim()).replaceAll("").trim();
        for (int i = 0; i < 4; i++) {
            String next = TAIL.matcher(s).replaceAll("").replaceAll("[\\s.,-]+$", "").trim();
            if (next.equals(s) || next.isEmpty()) {
                break;
            }
            s = next;
        }
        s = s.replaceFirst("^(?i)BCO\\b", "BANCO").replaceFirst("^(?i)AG\\.", "AGÊNCIA");
        List<String> out = new ArrayList<>();
        String[] words = s.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String u = w.toUpperCase();
            String bare = w.replace(".", "");
            if (i > 0 && LOWER.contains(u)) {
                out.add(u.toLowerCase());
            } else if (bare.length() <= 3 && w.chars().allMatch(Character::isLetter) && !NOT_ACRONYM.contains(u)) {
                out.add(u);
            } else {
                List<String> parts = new ArrayList<>();
                for (String p : w.split("-", -1)) {
                    parts.add(p.isEmpty() ? p : p.substring(0, 1).toUpperCase() + p.substring(1).toLowerCase());
                }
                out.add(String.join("-", parts));
            }
        }
        return String.join(" ", out);
    }
}
