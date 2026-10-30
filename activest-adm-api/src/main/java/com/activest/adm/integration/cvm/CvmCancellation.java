package com.activest.adm.integration.cvm;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registro cancelado na CVM ({@code cad_cia_aberta.csv}: {@code SIT = CANCELADA}, {@code DT_CANCEL},
 * {@code MOTIVO_CANCEL}) — a prova formal de saída da companhia (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, 10.7.3 e
 * C7). A companhia aparece uma vez por mercado: só conta como cancelada quando nenhuma das linhas está ATIVO.
 *
 * @param date   data do cancelamento (nula se a CVM não informar)
 * @param reason motivo, como a CVM escreve ("Cancelamento Voluntário", "Incorporação", "Migração de Registro"...)
 */
public record CvmCancellation(String cnpj, LocalDate date, String reason) {

    /**
     * "Migração de registro" (Resolução CVM 60) troca a categoria do registro e não é saída de bolsa: não prova nada
     * (10.7.3 — 47 casos desde 2010).
     */
    public boolean leftTheExchange() {
        String r = reason == null ? "" : Normalizer.normalize(reason, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase();
        return !r.contains("MIGRACAO");
    }

    /** As companhias canceladas do cadastro, pela raiz completa do CNPJ (14 dígitos). */
    public static Map<String, CvmCancellation> parse(InputStream in) {
        Map<String, CvmCancellation> cancelled = new HashMap<>();
        Set<String> active = new HashSet<>();
        CvmCsv.forEach(in, row -> {
            String cnpj = CvmCsv.cnpj(row.get("CNPJ_CIA"));
            if (cnpj == null) {
                return;
            }
            String sit = row.get("SIT");
            if ("ATIVO".equalsIgnoreCase(sit)) {
                active.add(cnpj);
            } else if (sit != null && sit.toUpperCase().startsWith("CANCELAD")) {
                cancelled.merge(cnpj, new CvmCancellation(cnpj, date(row.get("DT_CANCEL")), row.get("MOTIVO_CANCEL")),
                        (a, b) -> a.date() != null && (b.date() == null || !b.date().isAfter(a.date())) ? a : b);
            }
        });
        active.forEach(cancelled::remove);
        return cancelled;
    }

    private static LocalDate date(String s) {
        try {
            return s == null ? null : LocalDate.parse(s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
