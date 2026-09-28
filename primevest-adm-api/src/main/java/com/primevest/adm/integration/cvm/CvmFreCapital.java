package com.primevest.adm.integration.cvm;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Historico do capital social no Formulario de Referencia (FRE), arquivo
 * {@code fre_cia_aberta_capital_social_{ano}.csv}.
 *
 * <p>Cada FRE lista os eventos de capital aprovados (emitido, subscrito,
 * integralizado, autorizado) com a quantidade de acoes ON, PN e total, em
 * unidades. Aqui fica so o <b>capital integralizado</b>: e o que corresponde as
 * acoes existentes. Serve a duas coisas (PLANO_EMPRESAS_BALANCOS.md, F5):
 * quantidade de acoes antes de 2020, quando o DFP/ITR nao tinha a composicao do
 * capital, e regua da escala da composicao a partir de 2020.</p>
 */
public final class CvmFreCapital {

    /** Um evento de capital integralizado, como consta de um FRE ({@code referenceDate}, {@code version}). */
    public record Event(String cnpj, LocalDate referenceDate, int version, LocalDate approvalDate,
                        long ordinary, long preferred, long total) {
    }

    private CvmFreCapital() {
    }

    public static List<Event> read(Path zip, int year) {
        List<Event> out = new ArrayList<>();
        CvmZip.forEach(zip, "fre_cia_aberta_capital_social_" + year + ".csv", row -> {
            String type = CvmStatements.key(row.get("Tipo_Capital"));
            if (!type.equals("capital integralizado")) {
                return;
            }
            String cnpj = CvmCsv.cnpj(row.get("CNPJ_Companhia"));
            LocalDate ref = date(row.get("Data_Referencia"));
            LocalDate approval = date(row.get("Data_Autorizacao_Aprovacao"));
            long total = number(row.get("Quantidade_Total_Acoes"));
            if (cnpj == null || ref == null || approval == null || total <= 0) {
                return;
            }
            out.add(new Event(cnpj, ref, (int) number(row.get("Versao")), approval,
                    number(row.get("Quantidade_Acoes_Ordinarias")), number(row.get("Quantidade_Acoes_Preferenciais")),
                    total));
        });
        return out;
    }

    private static LocalDate date(String s) {
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long number(String s) {
        if (s == null) {
            return 0L;
        }
        try {
            return new BigDecimal(s.trim()).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
