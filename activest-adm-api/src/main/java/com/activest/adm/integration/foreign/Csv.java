package com.activest.adm.integration.foreign;

import java.util.ArrayList;
import java.util.List;

/** Divisão de uma linha de CSV com aspas (o campo entre aspas pode ter o separador; aspas dobradas = uma aspa). */
final class Csv {

    private Csv() {
    }

    static List<String> split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** O campo, sem espaços nas pontas; vazio ou ausente = nulo. */
    static String get(List<String> v, Integer i) {
        if (i == null || i >= v.size()) {
            return null;
        }
        String s = v.get(i).trim();
        return s.isEmpty() ? null : s;
    }
}
