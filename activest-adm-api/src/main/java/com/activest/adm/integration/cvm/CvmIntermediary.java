package com.activest.adm.integration.cvm;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Um participante intermediario do cadastro da CVM ({@code cad_intermed.csv}, dentro de {@code cad_intermed.zip};
 * docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 4.1).
 *
 * <p>O cadastro traz uma linha por tipo de participacao: o mesmo CNPJ aparece como CORRETORAS, CUSTODIANTES e
 * ESCRITURADORES. A chave e o CNPJ; os tipos se somam, e a situacao "em funcionamento" de qualquer linha vale.</p>
 */
public record CvmIntermediary(String cnpj, Set<String> types, String legalName, String tradeName, String status,
                              String website) {

    public static final String ACTIVE = "EM FUNCIONAMENTO NORMAL";

    /** Uma entrada por CNPJ. */
    public static Map<String, CvmIntermediary> parse(Path zip) {
        Map<String, CvmIntermediary> byCnpj = new LinkedHashMap<>();
        CvmZip.forEach(zip, "cad_intermed.csv", row -> {
            String cnpj = CvmCsv.cnpj(row.get("CNPJ"));
            if (cnpj == null) {
                return;
            }
            Set<String> types = new LinkedHashSet<>();
            if (row.get("TP_PARTIC") != null) {
                types.add(row.get("TP_PARTIC"));
            }
            CvmIntermediary c = new CvmIntermediary(cnpj, types, row.get("DENOM_SOCIAL"), row.get("DENOM_COMERC"),
                    row.get("SIT"), row.get("SITE_WEB"));
            byCnpj.merge(cnpj, c, CvmIntermediary::merge);
        });
        return byCnpj;
    }

    private static CvmIntermediary merge(CvmIntermediary a, CvmIntermediary b) {
        Set<String> types = new LinkedHashSet<>(a.types);
        types.addAll(b.types);
        boolean bActive = ACTIVE.equalsIgnoreCase(b.status) && !ACTIVE.equalsIgnoreCase(a.status);
        CvmIntermediary base = bActive ? b : a;
        return new CvmIntermediary(a.cnpj, types,
                base.legalName != null ? base.legalName : a.legalName,
                base.tradeName != null ? base.tradeName : (a.tradeName != null ? a.tradeName : b.tradeName),
                base.status,
                a.website != null ? a.website : b.website);
    }

    /** Algum dos tipos contem o trecho (sem diferenciar maiusculas). */
    public boolean hasType(String fragment) {
        return types.stream().anyMatch(t -> t.toUpperCase().contains(fragment.toUpperCase()));
    }
}
