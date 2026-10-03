package com.primevest.adm.integration.cvm;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Parte {@code geral} do informe mensal de FII: cadastro do fundo
 * (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.2). Uma linha por fundo por mes; o importador
 * fica com a mais recente de cada CNPJ.
 *
 * <p>O {@code Segmento_Atuacao} da CVM e fraco (metade dos fundos em bolsa como
 * "Multicategoria"): vai para {@code issuers.cvm_segment}, so como informacao.
 * O {@code Codigo_ISIN} pode vir vazio ou ser o da classe, diferente do
 * negociado — por isso a ligacao com o ativo nao depende dele.</p>
 */
public record CvmFiiGeneral(String cnpj, LocalDate referenceDate, int version, String name, String isin,
                            String cvmSegment, String managementType, boolean onExchange,
                            String administratorName, String administratorCnpj, String website) {

    /** Informe mais recente de cada CNPJ, do zip de um ano. */
    public static Map<String, CvmFiiGeneral> latest(Path zip, int year) {
        Map<String, CvmFiiGeneral> out = new HashMap<>();
        CvmZip.forEach(zip, "inf_mensal_fii_geral_" + year + ".csv", row -> {
            String cnpj = CvmCsv.cnpj(row.first("CNPJ_Fundo_Classe", "CNPJ_Fundo"));
            LocalDate ref = CvmFca.date(row.get("Data_Referencia"));
            if (cnpj == null || ref == null) {
                return;
            }
            CvmFiiGeneral g = new CvmFiiGeneral(cnpj, ref, CvmFca.integer(row.get("Versao"), 1),
                    row.first("Nome_Fundo_Classe", "Nome_Fundo"), row.get("Codigo_ISIN"),
                    row.get("Segmento_Atuacao"), row.get("Tipo_Gestao"),
                    "S".equalsIgnoreCase(row.get("Mercado_Negociacao_Bolsa")),
                    row.get("Nome_Administrador"), CvmCsv.cnpj(row.get("CNPJ_Administrador")), row.get("Site"));
            out.merge(cnpj, g, (a, b) -> CvmFca.newer(a.referenceDate, a.version, b.referenceDate, b.version) ? a : b);
        });
        return out;
    }
}
