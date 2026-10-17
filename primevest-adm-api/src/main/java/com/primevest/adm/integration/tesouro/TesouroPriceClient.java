package com.primevest.adm.integration.tesouro;

import com.primevest.adm.integration.PublicFileCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Precos e taxas historicas dos titulos do Tesouro Direto (Tesouro Transparente, dados abertos, sem token) —
 * docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R1.
 *
 * <p>Um arquivo so, {@code PrecoTaxaTesouroDireto.csv}, com todo o historico desde 2002 (~14 MB, ~1 min para baixar
 * em 05/10/2026), atualizado todo dia util pela manha com o D-1. Cache em
 * {@code carga/data/tesouro/PrecoTaxaTesouroDireto.csv} ({@link PublicFileCache}): sempre pergunta com
 * {@code If-Modified-Since} e so baixa quando mudou; fonte fora do ar fica com o disco.</p>
 *
 * <p>Formato: {@code Tipo Titulo;Data Vencimento;Data Base;Taxa Compra Manha;Taxa Venda Manha;PU Compra Manha;
 * PU Venda Manha;PU Base Manha}, datas {@code dd/MM/yyyy}, numeros com virgula.</p>
 */
@Component
public class TesouroPriceClient {

    static final String FILE_NAME = "PrecoTaxaTesouroDireto.csv";
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Uma linha: o titulo (tipo + vencimento), o dia, as taxas de compra e de venda da manha (% a.a.; nula quando
     * zero) e os PUs de venda (resgate) e base.
     */
    public record Row(String type, LocalDate maturity, LocalDate date, BigDecimal buyRate, BigDecimal sellRate,
                      BigDecimal sellPu, BigDecimal basePu) {
    }

    private final PublicFileCache files;
    private final URI url;
    private final Path target;

    @Autowired
    public TesouroPriceClient(@Value("${tesouro.prices-url:https://www.tesourotransparente.gov.br/ckan/dataset/df56aa42-484a-4a59-8184-7676580c81e3/resource/796d2059-14e9-44e3-80c9-2d9e30b405c1/download/PrecoTaxaTesouroDireto.csv}") String url,
                              @Value("${primevest.data.dir:../carga/data}") String dataDir,
                              @Value("${tesouro.timeout-seconds:180}") long timeoutSeconds) {
        this(URI.create(url), Path.of(dataDir, "tesouro", FILE_NAME), new PublicFileCache(Duration.ofSeconds(timeoutSeconds)));
    }

    TesouroPriceClient(URI url, Path target, PublicFileCache files) {
        this.url = url;
        this.target = target.toAbsolutePath().normalize();
        this.files = files;
    }

    /** O arquivo atualizado (baixa so se mudou). Fonte fora do ar e sem copia: {@link PublicFileCache.UnavailableException}. */
    public Path fetch() {
        return files.fetch(url, target, true)
                .orElseThrow(() -> new PublicFileCache.UnavailableException("Tesouro respondeu 404 para " + url, null));
    }

    /** Le o arquivo linha a linha; linha mal formada e pulada. */
    public static void read(Path file, Consumer<Row> sink) {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String header = in.readLine();
            if (header == null) {
                return;
            }
            String line;
            while ((line = in.readLine()) != null) {
                Row r = parse(line);
                if (r != null) {
                    sink.accept(r);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo do Tesouro ilegivel: " + file, e);
        }
    }

    static Row parse(String line) {
        String[] c = line.split(";", -1);
        if (c.length < 8) {
            return null;
        }
        try {
            return new Row(c[0].trim(), LocalDate.parse(c[1].trim(), BR), LocalDate.parse(c[2].trim(), BR),
                    number(c[3]), number(c[4]), number(c[6]), number(c[7]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal number(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        BigDecimal v = new BigDecimal(t.replace(".", "").replace(',', '.'));
        return v.signum() > 0 ? v : null;
    }
}
