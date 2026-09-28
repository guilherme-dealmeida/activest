package com.primevest.adm.integration.cvm;

/**
 * Conjuntos anuais dos dados abertos da CVM usados pelo PrimeVest
 * (PLANO_EMPRESAS_BALANCOS.md, 1.1 e 1.2). Cada ano e um zip.
 */
public enum CvmDataset {

    FCA("CIA_ABERTA/DOC/FCA/DADOS/fca_cia_aberta_%d.zip", "fca"),
    DFP("CIA_ABERTA/DOC/DFP/DADOS/dfp_cia_aberta_%d.zip", "dfp"),
    ITR("CIA_ABERTA/DOC/ITR/DADOS/itr_cia_aberta_%d.zip", "itr"),
    /** Formulario de Referencia: o historico do capital social da a quantidade de acoes antes de 2020. */
    FRE("CIA_ABERTA/DOC/FRE/DADOS/fre_cia_aberta_%d.zip", "fre"),
    FII_MONTHLY("FII/DOC/INF_MENSAL/DADOS/inf_mensal_fii_%d.zip", "fii");

    private final String pathPattern;
    private final String folder;

    CvmDataset(String pathPattern, String folder) {
        this.pathPattern = pathPattern;
        this.folder = folder;
    }

    public String remotePath(int year) {
        return String.format(pathPattern, year);
    }

    public String folder() {
        return folder;
    }

    public String fileName(int year) {
        String p = remotePath(year);
        return p.substring(p.lastIndexOf('/') + 1);
    }
}
