package com.activest.adm.integration.treasury;

import com.activest.adm.integration.PublicFileCache;
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
 * {@code If-Modified-Since} e so baixa quando mudou. So vira arquivo o que comeca com o cabecalho do Tesouro, e o que
 * fazer com a copia em disco quando a fonte recusa e decisao do servico (docs/planos/PLANO_FONTE_TESOURO.md, D2).</p>
 *
 * <p>Formato: {@code Tipo Titulo;Data Vencimento;Data Base;Taxa Compra Manha;Taxa Venda Manha;PU Compra Manha;
 * PU Venda Manha;PU Base Manha}, datas {@code dd/MM/yyyy}, numeros com virgula.</p>
 */
@Component
public class TreasuryPriceClient {

    static final String FILE_NAME = "PrecoTaxaTesouroDireto.csv";
    /** Inicio do cabecalho do arquivo: o que nao comeca assim nao e o arquivo do Tesouro (R1, R6). */
    public static final String HEADER_PREFIX = "Tipo Titulo;";
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
    public TreasuryPriceClient(@Value("${treasury.prices-url:https://www.tesourotransparente.gov.br/ckan/dataset/df56aa42-484a-4a59-8184-7676580c81e3/resource/796d2059-14e9-44e3-80c9-2d9e30b405c1/download/PrecoTaxaTesouroDireto.csv}") String url,
                              @Value("${activest.data.dir:../carga/data}") String dataDir,
                              @Value("${treasury.timeout-seconds:180}") long timeoutSeconds) {
        this(URI.create(url), Path.of(dataDir, "tesouro", FILE_NAME), new PublicFileCache(Duration.ofSeconds(timeoutSeconds)));
    }

    TreasuryPriceClient(URI url, Path target, PublicFileCache files) {
        this.url = url;
        this.target = target.toAbsolutePath().normalize();
        this.files = files;
    }

    /** Pergunta ao Tesouro (baixa so se mudou) e conta o que aconteceu, com o arquivo em disco, se houver. */
    public PublicFileCache.Result fetch() {
        return files.get(url, target, true, PublicFileCache.ContentCheck.csvHeader(HEADER_PREFIX));
    }

    /**
     * Le o arquivo linha a linha; linha mal formada e pulada. Arquivo sem o cabecalho do Tesouro e erro, nao "zero
     * linhas" (R6): foi assim que a pagina de recusa gravada em 21/10/2026 passou por arquivo valido.
     */
    public static void read(Path file, Consumer<Row> sink) {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            checkHeader(file, in.readLine());
            String line;
            while ((line = in.readLine()) != null) {
                Row r = parse(line);
                if (r != null) {
                    sink.accept(r);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo de precos do Tesouro ilegivel: " + e.getMessage(), e);
        }
    }

    /** O cabecalho do arquivo do Tesouro (precos ou vendas); BOM ignorado. */
    static void checkHeader(Path file, String header) throws IOException {
        String h = header == null ? "" : header.startsWith("\u00ef\u00bb\u00bf") ? header.substring(3) : header;
        if (!h.startsWith(HEADER_PREFIX)) {
            String start = h.length() > 60 ? h.substring(0, 60) + "..." : h;
            throw new IOException("nao e o arquivo do Tesouro (cabecalho \"" + start + "\"): " + file);
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
