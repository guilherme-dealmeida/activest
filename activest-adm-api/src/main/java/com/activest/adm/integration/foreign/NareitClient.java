package com.activest.adm.integration.foreign;

import com.activest.adm.integration.PublicFileCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Composição do índice FTSE Nareit All REITs ({@code FNUSIC{ano}.pdf}): cada REIT listado no ano, com o ticker e o setor
 * (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, D5, C4 e C6). Confere o tipo REIT do OpenFIGI, dá a categoria do REIT e o
 * ticker do REIT deslistado. Cache em {@code carga/data/nareit}; o PDF do ano corrente muda todo mês.
 */
@Slf4j
@Component
public class NareitClient {

    /** Uma linha: {@code 1 BXP Inc BXP Equity 10,788.4} ou {@code 3 Arbor Realty Trust ABR Mortgage Commercial Financing 85.0}. */
    private static final Pattern LINE = Pattern.compile(
            "^\\d+\\s+(.+)\\s+([A-Z][A-Z0-9.\\-/]{0,9})\\s+(Equity|Mortgage)(?:\\s+(.*?))?\\s+(\\d[\\d,]*\\.\\d+)\\s*$");
    private static final Pattern PROPERTY = Pattern.compile("^Property Sector:\\s*(.+?)\\s*$");

    private final PublicFileCache files;
    private final String baseUrl;
    private final Path dir;

    @Autowired
    public NareitClient(@Value("${foreign.nareit.url:https://www.reit.com/sites/default/files/returns}") String baseUrl,
                        @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this(baseUrl, Path.of(dataDir, "nareit"), new PublicFileCache(Duration.ofSeconds(60)));
    }

    NareitClient(String baseUrl, Path dir, PublicFileCache files) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dir = dir.toAbsolutePath().normalize();
        this.files = files;
    }

    /** O PDF do ano; vazio se a Nareit não tiver o ano. {@code refresh}: o ano corrente (muda todo mês). */
    public Optional<Path> constituents(int year, boolean refresh) {
        String name = "FNUSIC" + year + ".pdf";
        return files.fetch(URI.create(baseUrl + "/" + name), dir.resolve(name), refresh,
                PublicFileCache.ContentCheck.pdf());
    }

    /**
     * Um REIT do índice no ano. {@code investmentSector}: Equity ou Mortgage; {@code propertySector}: o título da seção
     * (Office, Retail, Industrial/Office...); {@code subsector}: o da linha, quando há (Apartments, Home Financing...).
     */
    public record Constituent(int year, String name, String ticker, String investmentSector, String propertySector,
                              String subsector, BigDecimal capitalization) {
    }

    public static List<Constituent> parse(Path pdf, int year) {
        String text;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            text = s.getText(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parseText(text, year);
    }

    static List<Constituent> parseText(String text, int year) {
        List<Constituent> out = new ArrayList<>();
        String property = null;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("Summary of Changes")) {
                break;
            }
            Matcher p = PROPERTY.matcher(line);
            if (p.matches()) {
                property = p.group(1);
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches() || line.contains("Totals")) {
                continue;
            }
            String sub = m.group(4) == null || m.group(4).isBlank() ? null : m.group(4).strip();
            out.add(new Constituent(year, m.group(1).strip(), m.group(2), m.group(3),
                    "Mortgage".equals(m.group(3)) ? "Mortgage" : property, sub,
                    new BigDecimal(m.group(5).replace(",", ""))));
        }
        return out;
    }
}
