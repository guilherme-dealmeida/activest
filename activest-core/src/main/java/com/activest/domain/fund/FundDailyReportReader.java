package com.activest.domain.fund;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Leitor do informe diário dos fundos da CVM ({@code inf_diario_fi_AAAAMM.zip} e o anual {@code inf_diario_fi_AAAA.zip}
 * de {@code HIST/}), PLANO_FUNDOS_INVESTIMENTO.md, C5.
 *
 * <p>O layout muda ao longo dos anos — só {@code CNPJ_FUNDO}; {@code TP_FUNDO;CNPJ_FUNDO}; e, depois da RCVM 175,
 * {@code TP_FUNDO_CLASSE;CNPJ_FUNDO_CLASSE;ID_SUBCLASSE} — e dentro do anual os meses alternam sem ordem. Por isso as
 * colunas são achadas pelo cabeçalho de cada CSV, nunca pela posição. Arquivos em ISO-8859-1.</p>
 *
 * <p>Leitura em fluxo: um ZIP anual tem ~3 milhões de linhas; quem chama filtra pela chave ({@code keyFilter}) antes de
 * qualquer conversão de número.</p>
 */
public final class FundDailyReportReader {

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    /** Uma linha do informe. {@code subclassId} nulo na classe; {@code key} = ticker do ativo (D3). */
    public record Row(String cnpj, String subclassId, String fundType, LocalDate date, BigDecimal quote,
                      BigDecimal totalValue, BigDecimal netWorth, BigDecimal inflow, BigDecimal outflow,
                      Integer holders) {

        public String key() {
            return InvestmentFund.ticker(cnpj, subclassId);
        }
    }

    /**
     * Maior cota aceita: a coluna de preço ({@code NUMERIC(18,8)} em {@code assets.current_price} e
     * {@code asset_price_history.close_price}) não guarda 10^10. O informe tem linhas com valor absurdo na cota (ex.:
     * 90.335.366.100,00 num FIC multimercado em 12/2020, o patrimônio na coluna errada); elas são descartadas como
     * dado inválido da fonte, como a cota zerada ou negativa.
     */
    public static final BigDecimal MAX_QUOTE = new BigDecimal("1E10");

    private FundDailyReportReader() {
    }

    /** Cota utilizável: positiva e menor que {@link #MAX_QUOTE}. */
    public static boolean usableQuote(BigDecimal quote) {
        return quote != null && quote.signum() > 0 && quote.compareTo(MAX_QUOTE) < 0;
    }

    /** Lê o ZIP (um ou vários CSVs dentro) e entrega as linhas cuja chave passa no filtro (nulo = todas). */
    public static void read(Path zip, Predicate<String> keyFilter, Consumer<Row> sink) {
        // Buffer grande: o ZipInputStream le em pedacos de 512 bytes, e no volume montado do Docker no Windows cada
        // leitura pequena custa caro (um anual de 3 milhoes de linhas levava ~2 minutos sem o buffer).
        try (InputStream in = new java.io.BufferedInputStream(Files.newInputStream(zip), 1 << 20)) {
            read(in, keyFilter, sink);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + zip.getFileName(), e);
        }
    }

    public static void read(InputStream zipStream, Predicate<String> keyFilter, Consumer<Row> sink) throws IOException {
        ZipInputStream zin = new ZipInputStream(zipStream);
        ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".csv")) {
                readCsv(new BufferedReader(new InputStreamReader(zin, LATIN1)), keyFilter, sink);
            }
        }
    }

    /** Todas as linhas filtradas, em lista (para arquivos pequenos ou filtros estreitos). */
    public static List<Row> readAll(Path zip, Predicate<String> keyFilter) {
        List<Row> rows = new ArrayList<>();
        read(zip, keyFilter, rows::add);
        return rows;
    }

    /** Não fecha o reader: ele é o fluxo do ZIP, que segue para o próximo CSV. */
    static void readCsv(BufferedReader reader, Predicate<String> keyFilter, Consumer<Row> sink) throws IOException {
        String header = reader.readLine();
        if (header == null) {
            return;
        }
        Columns c = Columns.of(header.replace("﻿", "").split(";", -1));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(";", -1);
            String cnpj = FundKeys.cnpj(get(f, c.cnpj));
            if (cnpj == null) {
                continue;
            }
            String sub = c.subclass < 0 ? null : blankToNull(get(f, c.subclass));
            String key = InvestmentFund.ticker(cnpj, sub);
            if (keyFilter != null && !keyFilter.test(key)) {
                continue;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(get(f, c.date).trim());
            } catch (RuntimeException e) {
                continue;
            }
            sink.accept(new Row(cnpj, sub, c.type < 0 ? null : blankToNull(get(f, c.type)), date,
                    decimal(get(f, c.quote)), decimal(get(f, c.total)), decimal(get(f, c.netWorth)),
                    decimal(get(f, c.inflow)), decimal(get(f, c.outflow)), integer(get(f, c.holders))));
        }
    }

    private record Columns(int type, int cnpj, int subclass, int date, int total, int quote, int netWorth, int inflow,
                           int outflow, int holders) {
        static Columns of(String[] h) {
            int cnpj = -1;
            int type = -1;
            for (int i = 0; i < h.length; i++) {
                String n = h[i].trim();
                if (n.startsWith("CNPJ_FUNDO")) {
                    cnpj = i;
                } else if (n.startsWith("TP_FUNDO")) {
                    type = i;
                }
            }
            if (cnpj < 0) {
                throw new IllegalArgumentException("Informe diário sem coluna CNPJ_FUNDO: " + String.join(";", h));
            }
            return new Columns(type, cnpj, idx(h, "ID_SUBCLASSE"), idx(h, "DT_COMPTC"), idx(h, "VL_TOTAL"),
                    idx(h, "VL_QUOTA"), idx(h, "VL_PATRIM_LIQ"), idx(h, "CAPTC_DIA"), idx(h, "RESG_DIA"),
                    idx(h, "NR_COTST"));
        }

        private static int idx(String[] h, String name) {
            for (int i = 0; i < h.length; i++) {
                if (h[i].trim().equals(name)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static String get(String[] f, int i) {
        return i >= 0 && i < f.length ? f[i] : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal decimal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
