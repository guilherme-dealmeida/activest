package com.primevest.adm.integration.cvm;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Leitor dos zips anuais de DFP e ITR da CVM (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.1).
 *
 * <p>So le e filtra; quem decide o que cada conta significa e o dominio
 * ({@code FinancialMetricRules}). Fica com:</p>
 * <ul>
 *   <li>linhas do exercicio {@code ULTIMO} (o {@code PENULTIMO} e o comparativo do ano anterior);</li>
 *   <li>contas ate o 4o nivel ({@code 3.99.01.01}), que e onde estao todas as metricas usadas;</li>
 *   <li>a versao mais alta de cada documento (o arquivo pode trazer reapresentacao);</li>
 *   <li>consolidado e individual separados — a escolha (D6) e do dominio;</li>
 *   <li>a composicao do capital, que so existe de 2020 em diante.</li>
 * </ul>
 * <p>{@code ST_CONTA_FIXA} nao filtra: as linhas de LPA ({@code 3.99.01.01}) vem com {@code N}.</p>
 */
public final class CvmStatements {

    /** Demonstracoes lidas. DMPL, DRA e parecer ficam de fora. */
    public enum Statement { BPA, BPP, DRE, DFC_MI, DFC_MD, DVA }

    /**
     * Uma conta. {@code value} como veio no arquivo; {@code thousands} diz se a
     * escala e {@code MIL}. {@code periodStart} nulo no balanco (posicao numa data).
     * {@code key} e a descricao sem acento e em minusculas, para as regras.
     */
    public record Line(Statement statement, String code, String description, String key, BigDecimal value,
                       boolean thousands, LocalDate periodStart, LocalDate periodEnd) {

        public int depth() {
            int dots = 0;
            for (int i = 0; i < code.length(); i++) {
                if (code.charAt(i) == '.') {
                    dots++;
                }
            }
            return dots;
        }
    }

    /** Composicao do capital como veio (sem escala conhecida — ver PLANO, 1.1, armadilhas). */
    public record CapitalComposition(long ordinary, long preferred, long total,
                                     long ordinaryTreasury, long preferredTreasury, long treasury) {
    }

    public record DocKey(String cnpj, LocalDate referenceDate) {
    }

    /** Um DFP ou ITR entregue: indice, contas e composicao do capital. */
    public static final class Document {
        private final String cnpj;
        private final LocalDate referenceDate;
        private int version = -1;
        private Integer cvmDocId;
        private LocalDate receivedAt;
        private String companyName;
        private final List<Line> consolidated = new ArrayList<>();
        private final List<Line> individual = new ArrayList<>();
        private CapitalComposition capital;

        Document(String cnpj, LocalDate referenceDate) {
            this.cnpj = cnpj;
            this.referenceDate = referenceDate;
        }

        public String cnpj() {
            return cnpj;
        }

        public LocalDate referenceDate() {
            return referenceDate;
        }

        public int version() {
            return version;
        }

        public Integer cvmDocId() {
            return cvmDocId;
        }

        public LocalDate receivedAt() {
            return receivedAt;
        }

        public String companyName() {
            return companyName;
        }

        public List<Line> consolidated() {
            return consolidated;
        }

        public List<Line> individual() {
            return individual;
        }

        public CapitalComposition capital() {
            return capital;
        }

        /** Versao nova descarta o que foi lido da anterior; versao velha e ignorada. */
        private boolean accept(int v) {
            if (v > version) {
                if (version >= 0) {
                    consolidated.clear();
                    individual.clear();
                    capital = null;
                }
                version = v;
            }
            return v == version;
        }
    }

    private CvmStatements() {
    }

    /**
     * Le um zip anual.
     *
     * @param prefix {@code dfp} ou {@code itr}
     * @param year   o ano do zip (entra no nome de cada arquivo)
     * @param cnpjs  quais companhias guardar (as emissoras); o resto e descartado na leitura
     */
    public static Map<DocKey, Document> read(Path zip, String prefix, int year, Predicate<String> cnpjs) {
        Map<DocKey, Document> docs = new HashMap<>();
        CvmZip.forEach(zip, prefix + "_cia_aberta_" + year + ".csv", row -> {
            Document d = doc(docs, row, cnpjs);
            Integer v = integer(row.get("VERSAO"));
            if (d == null || v == null || !d.accept(v)) {
                return;
            }
            d.cvmDocId = integer(row.get("ID_DOC"));
            d.receivedAt = date(row.get("DT_RECEB"));
            d.companyName = row.get("DENOM_CIA");
        });
        for (Statement s : Statement.values()) {
            for (boolean con : new boolean[]{true, false}) {
                String entry = prefix + "_cia_aberta_" + s.name() + (con ? "_con_" : "_ind_") + year + ".csv";
                CvmZip.forEach(zip, entry, row -> line(docs, s, con, row, cnpjs));
            }
        }
        CvmZip.forEach(zip, prefix + "_cia_aberta_composicao_capital_" + year + ".csv", row -> {
            Document d = doc(docs, row, cnpjs);
            Integer v = integer(row.get("VERSAO"));
            if (d == null || v == null || !d.accept(v)) {
                return;
            }
            d.capital = new CapitalComposition(
                    number(row.get("QT_ACAO_ORDIN_CAP_INTEGR")), number(row.get("QT_ACAO_PREF_CAP_INTEGR")),
                    number(row.get("QT_ACAO_TOTAL_CAP_INTEGR")), number(row.get("QT_ACAO_ORDIN_TESOURO")),
                    number(row.get("QT_ACAO_PREF_TESOURO")), number(row.get("QT_ACAO_TOTAL_TESOURO")));
        });
        docs.values().removeIf(d -> d.version < 0);
        return docs;
    }

    private static void line(Map<DocKey, Document> docs, Statement s, boolean con, CvmCsv.Row row,
                             Predicate<String> cnpjs) {
        String order = row.get("ORDEM_EXERC");
        if (order == null || order.toUpperCase(Locale.ROOT).startsWith("PEN")) {
            return;
        }
        String code = row.get("CD_CONTA");
        if (code == null || !wanted(s, code)) {
            return;
        }
        Document d = doc(docs, row, cnpjs);
        Integer v = integer(row.get("VERSAO"));
        BigDecimal value = decimal(row.get("VL_CONTA"));
        LocalDate end = date(row.get("DT_FIM_EXERC"));
        if (d == null || v == null || value == null || end == null || !d.accept(v)) {
            return;
        }
        String description = row.get("DS_CONTA");
        Line line = new Line(s, code, description, key(description), value,
                "MIL".equalsIgnoreCase(row.get("ESCALA_MOEDA")), date(row.get("DT_INI_EXERC")), end);
        (con ? d.consolidated : d.individual).add(line);
    }

    /** Contas que as regras podem usar: ate o 4o nivel, e so as do ativo e do caixa que interessam. */
    private static boolean wanted(Statement s, String code) {
        int dots = 0;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '.') {
                dots++;
            }
        }
        if (dots > 3) {
            return false;
        }
        return switch (s) {
            case BPA -> code.equals("1") || code.equals("1.01.01") || code.equals("1.01.02");
            case DFC_MI, DFC_MD -> code.equals("6.01");
            default -> true;
        };
    }

    private static Document doc(Map<DocKey, Document> docs, CvmCsv.Row row, Predicate<String> cnpjs) {
        String cnpj = CvmCsv.cnpj(row.get("CNPJ_CIA"));
        LocalDate ref = date(row.get("DT_REFER"));
        if (cnpj == null || ref == null || !cnpjs.test(cnpj)) {
            return null;
        }
        return docs.computeIfAbsent(new DocKey(cnpj, ref), k -> new Document(k.cnpj(), k.referenceDate()));
    }

    /** Descricao sem acento, minuscula e com espacos simples: "Atribuído a Sócios" -> "atribuido a socios". */
    public static String key(String description) {
        if (description == null) {
            return "";
        }
        String s = Normalizer.normalize(description, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
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

    private static Integer integer(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String s) {
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long number(String s) {
        BigDecimal d = decimal(s);
        return d == null ? 0L : d.longValue();
    }
}
