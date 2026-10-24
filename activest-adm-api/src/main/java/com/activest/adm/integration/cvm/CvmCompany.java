package com.activest.adm.integration.cvm;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uma companhia do cadastro da CVM ({@code cad_cia_aberta.csv}).
 *
 * <p>O cadastro traz uma linha por mercado — a Petrobras aparece em BOLSA e em
 * BALCAO ORGANIZADO. A chave e o CNPJ; {@link #activeOnExchange} diz se alguma
 * das linhas e ATIVO em BOLSA (criterio D2 do plano).</p>
 */
public record CvmCompany(String cnpj, int cvmCode, String legalName, String tradeName, String status,
                         String sector, String category, boolean activeOnExchange) {

    /** Uma entrada por CNPJ. */
    public static Map<String, CvmCompany> parse(InputStream in) {
        Map<String, CvmCompany> byCnpj = new LinkedHashMap<>();
        CvmCsv.forEach(in, row -> {
            String cnpj = CvmCsv.cnpj(row.get("CNPJ_CIA"));
            String code = row.get("CD_CVM");
            if (cnpj == null || code == null) {
                return;
            }
            boolean activeHere = "ATIVO".equalsIgnoreCase(row.get("SIT"))
                    && "BOLSA".equalsIgnoreCase(row.get("TP_MERC"));
            CvmCompany candidate = new CvmCompany(cnpj, Integer.parseInt(code.replaceAll("\\D", "")),
                    row.get("DENOM_SOCIAL"), row.get("DENOM_COMERC"), row.get("SIT"),
                    row.get("SETOR_ATIV"), row.get("CATEG_REG"), activeHere);
            byCnpj.merge(cnpj, candidate, CvmCompany::preferred);
        });
        return byCnpj;
    }

    /** Das duas linhas da mesma companhia, fica a de BOLSA ativa; a outra so soma a marca. */
    private static CvmCompany preferred(CvmCompany a, CvmCompany b) {
        CvmCompany base = b.activeOnExchange && !a.activeOnExchange ? b
                : (!"ATIVO".equalsIgnoreCase(a.status) && "ATIVO".equalsIgnoreCase(b.status) ? b : a);
        return new CvmCompany(base.cnpj, base.cvmCode, base.legalName, base.tradeName,
                "ATIVO".equalsIgnoreCase(a.status) || "ATIVO".equalsIgnoreCase(b.status) ? "ATIVO" : base.status,
                base.sector, base.category, a.activeOnExchange || b.activeOnExchange);
    }
}
