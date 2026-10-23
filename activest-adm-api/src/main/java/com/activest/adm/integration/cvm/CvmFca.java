package com.activest.adm.integration.cvm;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formulario Cadastral (FCA) das companhias abertas: {@code geral} (fim do
 * exercicio, site) e {@code valor_mobiliario} (ticker, composicao de unit,
 * segmento de listagem).
 *
 * <p>O FCA do ano so traz quem ja entregou; o importador le os ultimos anos e
 * fica com o documento mais recente de cada CNPJ (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.1).</p>
 */
public final class CvmFca {

    private CvmFca() {
    }

    public record General(String cnpj, LocalDate referenceDate, int version, Integer cvmCode,
                          Short fiscalYearEndMonth, String website) {
    }

    /**
     * Um valor mobiliario negociado. {@code unitComposition} e texto livre
     * ("1 KLBN3 + 4 KLBN4"); {@code preferredClass} e a sigla da classe da
     * preferencial (PNA, PNB...), quando a companhia tem mais de uma.
     */
    public record Security(String cnpj, LocalDate referenceDate, String type, String preferredClass, String ticker,
                           String unitComposition, String market, String listingSegment, LocalDate tradingEnd) {

        public boolean onExchange() {
            return market != null && market.equalsIgnoreCase("Bolsa");
        }
    }

    /** Documento {@code geral} mais recente por CNPJ, do zip de um ano. */
    public static Map<String, General> general(Path zip, int year) {
        Map<String, General> out = new HashMap<>();
        CvmZip.forEach(zip, "fca_cia_aberta_geral_" + year + ".csv", row -> {
            String cnpj = CvmCsv.cnpj(row.get("CNPJ_Companhia"));
            LocalDate ref = date(row.get("Data_Referencia"));
            if (cnpj == null || ref == null) {
                return;
            }
            String month = row.get("Mes_Encerramento_Exercicio_Social");
            String code = row.get("Codigo_CVM");
            General g = new General(cnpj, ref, integer(row.get("Versao"), 1),
                    code == null ? null : Integer.valueOf(code.replaceAll("\\D", "")),
                    month == null ? null : Short.valueOf(month.trim()),
                    row.get("Pagina_Web"));
            out.merge(cnpj, g, (a, b) -> newer(a.referenceDate, a.version, b.referenceDate, b.version) ? a : b);
        });
        return out;
    }

    /** Valores mobiliarios do documento mais recente de cada CNPJ, do zip de um ano. */
    public static Map<String, List<Security>> securities(Path zip, int year) {
        Map<String, LocalDate> latest = new HashMap<>();
        List<Security> all = new ArrayList<>();
        CvmZip.forEach(zip, "fca_cia_aberta_valor_mobiliario_" + year + ".csv", row -> {
            String cnpj = CvmCsv.cnpj(row.get("CNPJ_Companhia"));
            LocalDate ref = date(row.get("Data_Referencia"));
            if (cnpj == null || ref == null) {
                return;
            }
            all.add(new Security(cnpj, ref, row.get("Valor_Mobiliario"), upper(row.get("Sigla_Classe_Acao_Preferencial")),
                    upper(row.get("Codigo_Negociacao")),
                    row.get("Composicao_BDR_Unit"), row.get("Mercado"), row.get("Segmento"),
                    date(row.get("Data_Fim_Negociacao"))));
            latest.merge(cnpj, ref, (a, b) -> a.isAfter(b) ? a : b);
        });
        Map<String, List<Security>> out = new HashMap<>();
        for (Security s : all) {
            if (s.referenceDate().equals(latest.get(s.cnpj()))) {
                out.computeIfAbsent(s.cnpj(), k -> new ArrayList<>()).add(s);
            }
        }
        return out;
    }

    static boolean newer(LocalDate d1, int v1, LocalDate d2, int v2) {
        return d1.isAfter(d2) || (d1.equals(d2) && v1 >= v2);
    }

    static LocalDate date(String s) {
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static int integer(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }
}
