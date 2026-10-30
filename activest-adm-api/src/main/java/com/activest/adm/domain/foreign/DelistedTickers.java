package com.activest.adm.domain.foreign;

import com.activest.adm.integration.foreign.SecClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O ticker de um papel que saiu da bolsa (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C6): o Form 25 não traz o
 * símbolo. Ordem: {@code dei:TradingSymbol} do XBRL dos últimos formulários antes da saída (inline desde 2019, inclusive no
 * 8-K; antes, a instância XBRL do 10-K/10-Q) → o texto do relatório ("under the symbol ...", "(NYSE: XYZ)").
 * Prova das fontes (29/10/2026): 2024T1, 122 de 148 empresas; 2013T2, 31 de 56 com uma versão mais simples.
 */
public final class DelistedTickers {

    /** Formulários com XBRL inline que trazem a capa ({@code dei:TradingSymbol}). */
    static final Set<String> INLINE = Set.of("8-K", "10-Q", "10-K", "10-K/A", "10-KT", "20-F", "40-F", "6-K", "N-CSR",
            "N-CSRS");
    /** Relatórios periódicos (texto e instância XBRL antiga). */
    static final Set<String> REPORTS = Set.of("10-K", "10-Q", "10-K/A", "10-KT", "20-F", "40-F", "10-K405", "10-KSB",
            "10-QSB", "N-CSR", "N-CSRS");
    static final int MAX_DOCUMENTS = 5;
    static final int MAX_BYTES = 3_000_000;

    private static final Pattern SYMBOL = Pattern.compile(
            "dei:TradingSymbol[^>]*>\\s*(?:<[^>]+>\\s*)*([A-Za-z][A-Za-z.\\-]{0,7})\\s*<", Pattern.CASE_INSENSITIVE);
    private static final String QUOTE = "(?:&#8220;|&#147;|&ldquo;|&quot;|“|\"|')?";
    private static final Pattern TEXT = Pattern.compile(
            "(?:under the (?:trading |ticker |stock )?symbols?|ticker symbols?|trading symbols?)(?:\\s+of)?\\s*:?\\s*"
                    + QUOTE + "\\s*(?:<[^>]+>\\s*)*" + QUOTE + "\\s*([A-Z]{1,5}(?:\\.[A-Z])?)\\b");
    private static final Pattern EXCHANGE_COLON = Pattern.compile(
            "\\((?:NYSE|NASDAQ|Nasdaq|AMEX|NYSE MKT|NYSE Amex|NYSE American|NYSE Arca)\\s*:\\s*([A-Z]{1,5}(?:\\.[A-Z])?)\\)");
    private static final Pattern NOT_A_TICKER = Pattern.compile("^(THE|AND|OF|FOR|NYSE|NASDAQ|AMEX|INC|LLC|CORP|N|A)$");

    private final SecClient sec;

    public DelistedTickers(SecClient sec) {
        this.sec = sec;
    }

    /** O achado: ticker, origem ({@code XBRL} ou {@code FILING_TEXT}) e o documento. */
    public record Found(String ticker, String source, String document) {
    }

    /**
     * O ticker da empresa antes de {@code before} (a data do Form 25). Vazio se nenhum dos {@value #MAX_DOCUMENTS}
     * documentos mais recentes disser.
     */
    public Optional<Found> find(String cik, LocalDate before) {
        Optional<JsonNode> sub = sec.submissionsJson(cik);
        if (sub.isEmpty()) {
            return Optional.empty();
        }
        List<Filing> filings = filings(sub.get().path("filings").path("recent"));
        // empresa com muitos formulários: a lista recente pode começar depois da saída; a página anterior completa
        if (!filings.isEmpty() && filings.get(filings.size() - 1).date().isAfter(before)) {
            for (JsonNode f : sub.get().path("filings").path("files")) {
                String name = f.path("name").asText();
                LocalDate to = parse(f.path("filingTo").asText());
                LocalDate from = parse(f.path("filingFrom").asText());
                if (from != null && to != null && !from.isAfter(before) && !name.isEmpty()) {
                    sec.readText(sec.dataUrl("submissions/" + name), 20_000_000).ifPresent(t -> {
                        try {
                            filings.addAll(filings(new com.fasterxml.jackson.databind.ObjectMapper().readTree(t)));
                        } catch (java.io.IOException ignored) {
                            // página ilegível: segue com a recente
                        }
                    });
                    break;
                }
            }
        }
        int docs = 0;
        for (Filing f : filings) {
            if (f.date().isAfter(before) || docs >= MAX_DOCUMENTS) {
                continue;
            }
            boolean inline = f.inline() && INLINE.contains(f.form());
            boolean report = REPORTS.contains(f.form());
            if (!inline && !report) {
                continue;
            }
            if (!inline && f.xbrl()) {
                docs++;
                Optional<Found> x = instance(cik, f);
                if (x.isPresent()) {
                    return x;
                }
            }
            if (inline || report) {
                docs++;
                String url = sec.archiveUrl(cik, f.accession(), f.document());
                Optional<String> text = sec.readText(url, MAX_BYTES);
                if (text.isPresent()) {
                    Optional<Found> x = extract(text.get(), url, inline);
                    if (x.isPresent()) {
                        return x;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** O ticker num documento: o XBRL, depois o texto. */
    static Optional<Found> extract(String text, String url, boolean xbrl) {
        if (xbrl || text.contains("dei:TradingSymbol")) {
            Matcher m = SYMBOL.matcher(text);
            while (m.find()) {
                String t = m.group(1).toUpperCase();
                if (!NOT_A_TICKER.matcher(t).matches() && !t.equals("NONE") && !t.equals("NA")) {
                    return Optional.of(new Found(t, "XBRL", url));
                }
            }
        }
        for (Pattern p : List.of(TEXT, EXCHANGE_COLON)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String t = m.group(1);
                if (!NOT_A_TICKER.matcher(t).matches()) {
                    return Optional.of(new Found(t, "FILING_TEXT", url));
                }
            }
        }
        return Optional.empty();
    }

    /** A instância XBRL (antes do inline) pelo índice do formulário. */
    private Optional<Found> instance(String cik, Filing f) {
        Optional<String> idx = sec.readText(sec.archiveUrl(cik, f.accession(), "index.json"), 2_000_000);
        if (idx.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode items = new com.fasterxml.jackson.databind.ObjectMapper().readTree(idx.get())
                    .path("directory").path("item");
            for (JsonNode it : items) {
                String name = it.path("name").asText();
                if (name.endsWith(".xml") && !name.startsWith("FilingSummary")
                        && !name.matches(".*_(cal|def|lab|pre)\\.xml$")) {
                    String url = sec.archiveUrl(cik, f.accession(), name);
                    Optional<String> t = sec.readText(url, MAX_BYTES);
                    if (t.isPresent()) {
                        Matcher m = SYMBOL.matcher(t.get());
                        if (m.find()) {
                            return Optional.of(new Found(m.group(1).toUpperCase(), "XBRL", url));
                        }
                    }
                    break;
                }
            }
        } catch (java.io.IOException ignored) {
            // índice ilegível: segue para o texto
        }
        return Optional.empty();
    }

    private record Filing(String form, LocalDate date, String accession, String document, boolean xbrl,
                          boolean inline) {
    }

    private static List<Filing> filings(JsonNode recent) {
        List<Filing> out = new ArrayList<>();
        JsonNode form = recent.path("form");
        for (int i = 0; i < form.size(); i++) {
            LocalDate d = parse(recent.path("filingDate").path(i).asText());
            if (d == null) {
                continue;
            }
            out.add(new Filing(form.get(i).asText(), d, recent.path("accessionNumber").path(i).asText(),
                    recent.path("primaryDocument").path(i).asText(), recent.path("isXBRL").path(i).asInt() == 1,
                    recent.path("isInlineXBRL").path(i).asInt() == 1));
        }
        return out;
    }

    private static LocalDate parse(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
