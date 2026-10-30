package com.activest.adm.integration.foreign;

import com.activest.adm.integration.PublicFileCache;
import com.activest.domain.asset.ForeignExchange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista dos papéis negociados nos EUA do Nasdaq Trader ({@code nasdaqtraded.txt}) — o universo vivo da C1
 * (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, I1 e 11.3). Uma fotografia por dia em
 * {@code carga/data/nasdaqtrader/{data}.txt}: a série das fotografias é o histórico de quem sumiu (C7, exterior).
 */
@Slf4j
@Component
public class NasdaqTraderClient {

    private final PublicFileCache files;
    private final URI url;
    private final Path dir;

    @Autowired
    public NasdaqTraderClient(
            @Value("${foreign.nasdaqtrader.url:https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqtraded.txt}") String url,
            @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this(URI.create(url), Path.of(dataDir, "nasdaqtrader"), new PublicFileCache(Duration.ofSeconds(60)));
    }

    NasdaqTraderClient(URI url, Path dir, PublicFileCache files) {
        this.url = url;
        this.dir = dir.toAbsolutePath().normalize();
        this.files = files;
    }

    /** A fotografia do dia (baixada uma vez por dia; a do disco, se a fonte não responder). */
    public Path snapshot(LocalDate day) {
        Path target = dir.resolve(day + ".txt");
        return files.fetch(url, target, false, PublicFileCache.ContentCheck.csvHeader("Nasdaq Traded|Symbol|"))
                .orElseThrow(() -> new PublicFileCache.UnavailableException("Nasdaq Trader sem a lista de " + day, null));
    }

    /** As fotografias guardadas, da mais antiga à mais nova (nome = data). */
    public List<Path> snapshots() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.txt")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Uma linha da lista. {@code symbol}: o ticker na forma do Nasdaq ({@code BRK.B}, preferencial {@code ABR$E});
     * {@code exchange}: nulo fora das bolsas da D1.
     */
    public record Listing(String symbol, String name, ForeignExchange exchange, String exchangeCode, boolean etf,
                          boolean testIssue, String financialStatus, String cqsSymbol) {

        /** Preferencial: o Nasdaq escreve com {@code $} (ABR$E). */
        public boolean preferred() {
            return symbol.contains("$");
        }
    }

    /** Lê a lista (separada por {@code |}, cabeçalho na 1ª linha, "File Creation Time" na última). */
    public static List<Listing> parse(Path file) {
        List<Listing> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String header = r.readLine();
            if (header == null) {
                return out;
            }
            Map<String, Integer> col = new HashMap<>();
            String[] h = header.split("\\|", -1);
            for (int i = 0; i < h.length; i++) {
                col.put(h[i].trim(), i);
            }
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("File Creation Time") || line.isBlank()) {
                    continue;
                }
                String[] v = line.split("\\|", -1);
                String symbol = get(v, col, "Symbol");
                if (symbol == null || !"Y".equals(get(v, col, "Nasdaq Traded"))) {
                    continue;
                }
                String code = get(v, col, "Listing Exchange");
                out.add(new Listing(symbol, get(v, col, "Security Name"), ForeignExchange.ofNasdaqTraderCode(code), code,
                        "Y".equals(get(v, col, "ETF")), "Y".equals(get(v, col, "Test Issue")),
                        get(v, col, "Financial Status"), get(v, col, "CQS Symbol")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static String get(String[] v, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i >= v.length) {
            return null;
        }
        String s = v[i].trim();
        return s.isEmpty() ? null : s;
    }
}
