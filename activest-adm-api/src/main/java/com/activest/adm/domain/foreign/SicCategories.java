package com.activest.adm.domain.foreign;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Tabela SIC da SEC → setor da ação (categoria do STOCK, os 19 setores dos BDRs) — a segunda fonte da categoria (C4,
 * docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md), depois do BDR da mesma empresa. Arquivo versionado
 * {@code foreign/sic-categories.csv}; SIC sem setor (ADR, governo) fica para a curadoria.
 */
@Component
public class SicCategories {

    private final Map<String, String> category = new HashMap<>();
    private final Map<String, String> description = new HashMap<>();
    private final Map<String, String> byGroup = new HashMap<>();

    public SicCategories() {
        try (InputStream in = getClass().getResourceAsStream("/foreign/sic-categories.csv")) {
            if (in == null) {
                throw new IllegalStateException("foreign/sic-categories.csv ausente");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] v = line.split(";", -1);
                String sic = normalize(v[0]);
                description.put(sic, v[1]);
                if (v.length > 2 && !v[2].isBlank()) {
                    category.put(sic, v[2].trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Map<String, Integer>> votes = new HashMap<>();
        category.forEach((sic, cat) -> votes.computeIfAbsent(sic.substring(0, 2), k -> new HashMap<>()).merge(cat, 1,
                Integer::sum));
        votes.forEach((g, m) -> byGroup.put(g, m.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow()
                .getKey()));
    }

    /**
     * O setor do SIC, ou nulo. Código fora da tabela (a SEC ainda usa alguns antigos, como 6200 e 3823): o setor mais
     * comum do grupo de 2 dígitos.
     */
    public String category(String sic) {
        if (sic == null) {
            return null;
        }
        String s = normalize(sic);
        if (description.containsKey(s)) {
            return category.get(s);
        }
        return byGroup.get(s.substring(0, 2));
    }

    public String description(String sic) {
        return sic == null ? null : description.get(normalize(sic));
    }

    private static String normalize(String sic) {
        String s = sic.trim();
        return s.length() < 4 ? "0".repeat(4 - s.length()) + s : s;
    }
}
