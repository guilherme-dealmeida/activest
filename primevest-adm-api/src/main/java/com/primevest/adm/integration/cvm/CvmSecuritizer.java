package com.primevest.adm.integration.cvm;

import java.nio.file.Path;
import java.util.Map;

/**
 * Securitizadoras dos informes mensais de CRI e CRA (parte geral; docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 4.1):
 * CNPJ e o nome que a propria securitizadora informa (texto livre — a curadoria corrige no seed, secao 9).
 */
public final class CvmSecuritizer {

    private CvmSecuritizer() {
    }

    /**
     * Le o {@code inf_mensal_{cri|cra}_geral_{ano}.csv} do zip anual e acrescenta CNPJ -> nome em {@code out}
     * (o informe mais recente de cada CNPJ fica).
     */
    public static void parse(Path zip, String product, int year, Map<String, String> out, Map<String, String> dates) {
        CvmZip.forEach(zip, "inf_mensal_" + product + "_geral_" + year + ".csv", row -> {
            String cnpj = CvmCsv.cnpj(row.get("CNPJ_Emissora"));
            String name = row.get("Companhia_Emissora");
            if (cnpj == null || name == null) {
                return;
            }
            String date = row.get("Data_Referencia");
            String seen = dates.get(cnpj);
            if (seen == null || (date != null && date.compareTo(seen) >= 0)) {
                out.put(cnpj, name);
                dates.put(cnpj, date != null ? date : "");
            }
        });
    }
}
