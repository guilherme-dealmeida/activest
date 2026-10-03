package com.primevest.adm.integration.cvm;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Relatorio Proventos" da CVM (PLANO_CARGA_INICIAL.md, 5.2 — P3): o formulario que a companhia
 * entrega no Empresas.NET a cada provento, publicado no RAD como PDF e listado no indice IPE dos
 * dados abertos (categoria "Relatorio Proventos", de 2022 em diante).
 *
 * <p>E gerado de um formulario, entao o texto tem sempre a mesma forma (PDFBox, ordenado pela
 * posicao):</p>
 * <pre>
 * Data Aprovacao Ultimo dia de negociacao com Direitos
 * 11/12/2025 22/12/2025
 * ...
 * BRPETRACN 0,4716037800 Anual 2025 Nao A Vista 20/02/2026
 * OR9 0
 * ...
 * Parcelamento
 * Dia de Pagamento das Parcelas Valor Bruto (R$/Unidade)
 * 30/06/2026 0.11840131614
 * </pre>
 * <p>O ISIN (12 posicoes) e o valor (11 casas) quebram na coluna: a segunda linha traz o fim de
 * cada um. O ultimo dia "com" e a data-com do evento.</p>
 *
 * <p><b>Formato de 2022</b> (antes da troca do gerador, em 2023): datas do cabecalho em ingles
 * ({@code 11/21/2022 12:00:00 AM}), valor com ponto ({@code 1.7300000000}), "Havera atualizacao"
 * como {@code N}/{@code S} e forma de pagamento {@code V}/{@code P}. A data de pagamento do provento
 * vem as vezes dd/MM, as vezes MM/dd: vale a leitura que da uma data possivel e nao anterior a
 * data-com, com dd/MM na duvida.</p>
 */
public final class CvmProventosReport {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Aprovacao e data-com; em 2022 no formato americano, com hora. */
    private static final Pattern HEADER_DATES = Pattern.compile(
            "com Direitos\\s+(\\d{1,2}/\\d{1,2}/\\d{4})(\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M)?\\s+"
                    + "(\\d{1,2}/\\d{1,2}/\\d{4})(\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M)?");
    /** BRPETRACN 0,4716037800 Anual 2025 Nao A Vista 20/02/2026 */
    private static final Pattern ITEM = Pattern.compile(
            "^(BR[A-Z0-9]{3,11})\\s+(\\d+[.,]\\d+)\\s+(.*?)\\b(Sim|Nao|S|N)\\b\\s*(.*?)\\s*(\\d{1,2}/\\d{1,2}/\\d{4})?\\s*$");
    /** OR9 0 (fim do ISIN e fim do valor; as vezes com o exercicio na mesma linha) */
    private static final Pattern ITEM_TAIL = Pattern.compile("^([A-Z0-9]{1,9})(?:\\s+(\\d{1,4}))?(?:\\s+\\d{4})?\\s*$");
    /** 30/06/2026 0.11840131614 (data pode faltar: "a definir") */
    private static final Pattern PARCEL = Pattern.compile("^(?:(\\d{1,2}/\\d{1,2}/\\d{4})\\s+)?(\\d+[.,]\\d+)\\s*$");

    /** Uma parcela: data (nula = a definir) e valor bruto por acao. */
    public record Parcel(LocalDate paymentDate, BigDecimal value) {
    }

    /**
     * Um provento de uma especie: ISIN, valor bruto por acao, se e a vista, data de pagamento (a
     * vista) e as parcelas (parcelado, na ordem do relatorio).
     */
    public record Item(String isin, BigDecimal value, boolean installments, LocalDate paymentDate, List<Parcel> parcels) {
    }

    /** O relatorio: aprovacao, data-com e os proventos. */
    public record Report(LocalDate approvedOn, LocalDate dataCom, List<Item> items) {
    }

    private CvmProventosReport() {
    }

    /** Texto do PDF, na ordem da pagina. */
    public static String text(Path pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IOException e) {
            throw new CvmException("PDF ilegivel: " + pdf.getFileName() + " (" + e.getMessage() + ")", e);
        }
    }

    /** Le o relatorio a partir do texto; sem data-com ou sem proventos, lista vazia. */
    public static Report parse(String rawText) {
        String text = plain(rawText == null ? "" : rawText);
        Matcher header = HEADER_DATES.matcher(text);
        LocalDate approvedOn = null;
        LocalDate dataCom = null;
        if (header.find()) {
            boolean us = header.group(2) != null || header.group(4) != null;
            approvedOn = us ? usDate(header.group(1)) : date(header.group(1));
            dataCom = us ? usDate(header.group(3)) : date(header.group(3));
        }
        String[] lines = text.split("\\R");
        List<Item> items = new ArrayList<>();
        List<Parcel> parcels = new ArrayList<>();
        boolean inParcels = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("Dia de Pagamento das Parcelas")) {
                inParcels = true;
                continue;
            }
            if (inParcels) {
                Matcher p = PARCEL.matcher(line);
                if (p.matches()) {
                    parcels.add(new Parcel(paymentDate(p.group(1), dataCom), decimal(p.group(2))));
                }
                continue;
            }
            Matcher m = ITEM.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String isin = m.group(1);
            String value = m.group(2);
            if (i + 1 < lines.length) {
                Matcher tail = ITEM_TAIL.matcher(lines[i + 1].trim());
                if (tail.matches() && isin.length() < 12) {
                    isin = isin + tail.group(1);
                    if (tail.group(2) != null) {
                        value = value + tail.group(2);
                    }
                    i++;
                }
            }
            if (isin.length() != 12) {
                continue;
            }
            String form = m.group(5) == null ? "" : m.group(5).trim().toLowerCase();
            boolean installments = form.startsWith("parcel") || form.equals("p");
            items.add(new Item(isin, decimal(value), installments, paymentDate(m.group(6), dataCom), List.of()));
        }
        return new Report(approvedOn, dataCom, distributeParcels(items, parcels));
    }

    /**
     * As parcelas vem numa lista so, depois dos proventos: k parcelas para cada provento parcelado,
     * na ordem dos proventos. Se a conta nao fecha, nenhuma parcela e atribuida.
     */
    private static List<Item> distributeParcels(List<Item> items, List<Parcel> parcels) {
        List<Integer> parceled = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).installments()) {
                parceled.add(i);
            }
        }
        if (parceled.isEmpty() || parcels.isEmpty() || parcels.size() % parceled.size() != 0) {
            return items;
        }
        int k = parcels.size() / parceled.size();
        List<Item> out = new ArrayList<>(items);
        for (int j = 0; j < parceled.size(); j++) {
            Item it = items.get(parceled.get(j));
            List<Parcel> mine = new ArrayList<>(parcels.subList(j * k, (j + 1) * k));
            mine.sort(Comparator.comparing(Parcel::paymentDate, Comparator.nullsLast(Comparator.naturalOrder())));
            out.set(parceled.get(j), new Item(it.isin(), it.value(), true, null, List.copyOf(mine)));
        }
        return out;
    }

    // ------------------------------------------------------------------ indice IPE

    /** Uma entrega de "Relatorio Proventos" no indice IPE (so a maior versao de cada protocolo). */
    public record Entry(String cvmCode, String company, LocalDate referenceDate, long protocol, int version, String link) {
    }

    private static final Pattern LINK = Pattern.compile("numProtocolo=(\\d+).*?numVersao=(\\d+)");

    /** Os relatorios de proventos do zip anual do IPE; a versao mais nova de cada protocolo. */
    public static List<Entry> entries(Path ipeZip, int year) {
        Map<Long, Entry> latest = new LinkedHashMap<>();
        CvmZip.forEach(ipeZip, "ipe_cia_aberta_" + year + ".csv", row -> {
            String category = plain(row.get("Categoria")).toLowerCase();
            if (!category.startsWith("relatorio") || !category.contains("provent")) {
                return;
            }
            String link = row.get("Link_Download");
            Matcher m = link == null ? null : LINK.matcher(link);
            if (m == null || !m.find()) {
                return;
            }
            long protocol = Long.parseLong(m.group(1));
            int version = Integer.parseInt(m.group(2));
            Entry e = new Entry(row.get("Codigo_CVM"), row.get("Nome_Companhia"),
                    parseIso(row.get("Data_Referencia")), protocol, version, link.trim());
            latest.merge(protocol, e, (a, b) -> a.version() >= b.version() ? a : b);
        });
        return new ArrayList<>(latest.values());
    }

    // ------------------------------------------------------------------ auxiliares

    /** Sem acento (Nao, Relatorio): o PDF e o CSV da CVM variam na codificacao. */
    static String plain(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace(' ', ' ');
    }

    private static LocalDate date(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(s.trim(), BR);
            return d.getYear() <= 1900 || d.getYear() >= 9999 ? null : d;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 11/21/2022 (cabecalho de 2022). */
    private static LocalDate usDate(String s) {
        String[] p = s.trim().split("/");
        try {
            return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Pagamento mais distante da data-com que ainda e aceito (a CEB pagou em 2024 dividendos com data-com de 2019); alem disso e marcador ("a definir"). */
    static final int MAX_YEARS_AFTER_DATA_COM = 10;

    /**
     * Data de pagamento: dd/MM ou, no formato de 2022, as vezes MM/dd. Vale a leitura que fica entre
     * a data-com e {@link #MAX_YEARS_AFTER_DATA_COM} anos depois; nas duas possiveis, dd/MM. Fora
     * disso (01/01/0001, 31/12/9999 e afins, que as empresas usam para "a definir") nao ha data.
     */
    static LocalDate paymentDate(String s, LocalDate dataCom) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] p = s.trim().split("/");
        LocalDate br = null;
        LocalDate us = null;
        try {
            br = LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
        } catch (RuntimeException ignored) {
            // mes invalido: nao e dd/MM
        }
        try {
            us = LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (RuntimeException ignored) {
            // dia/mes invalido: nao e MM/dd
        }
        if (plausible(br, dataCom)) {
            return br;
        }
        if (plausible(us, dataCom)) {
            return us;
        }
        return null;
    }

    private static boolean plausible(LocalDate d, LocalDate dataCom) {
        if (d == null) {
            return false;
        }
        if (dataCom == null) {
            return d.getYear() >= 1990 && d.getYear() <= 2100;
        }
        return !d.isBefore(dataCom) && !d.isAfter(dataCom.plusYears(MAX_YEARS_AFTER_DATA_COM));
    }

    private static LocalDate parseIso(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.trim().substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String s) {
        String t = s.trim();
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(t);
    }
}
