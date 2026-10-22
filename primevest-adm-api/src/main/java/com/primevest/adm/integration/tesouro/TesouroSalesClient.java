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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Vendas diarias do Tesouro Direto (Tesouro Transparente, dados abertos, sem token): diz quais titulos o Tesouro
 * esta oferecendo hoje — o arquivo de precos traz taxa de todos, inclusive os que sairam da prateleira
 * (docs/planos/PLANO_RENTABILIDADE.md, andamento 17/10/2026).
 *
 * <p>{@code VendasTesouroDireto.csv} (~7,5 MB em 17/10/2026, atualizado todo dia util pela manha), cache em
 * {@code carga/data/tesouro/VendasTesouroDireto.csv} ({@link PublicFileCache}, so baixa quando mudou). Formato:
 * {@code Tipo Titulo;Vencimento do Titulo;Data de Liquidacao da Venda;PU;Quantidade;Valor}.</p>
 */
@Component
public class TesouroSalesClient {

    static final String FILE_NAME = "VendasTesouroDireto.csv";
    /** Titulo a venda = vendeu em algum dos ultimos {@value} dias de liquidacao do arquivo. */
    public static final int RECENT_DAYS = 3;
    /** Inicio do cabecalho do arquivo: o que nao comeca assim nao e o arquivo do Tesouro (R1, R6). */
    public static final String HEADER_PREFIX = "Tipo Titulo;";
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Titulo (tipo + vencimento). */
    public record Title(String type, LocalDate maturity) {
    }

    /** Os titulos vendidos nos ultimos dias do arquivo e o ultimo dia de liquidacao. */
    public record OnSale(LocalDate lastDate, Set<Title> titles) {
    }

    private final PublicFileCache files;
    private final URI url;
    private final Path target;

    @Autowired
    public TesouroSalesClient(@Value("${tesouro.sales-url:https://www.tesourotransparente.gov.br/ckan/dataset/f0468ecc-ae97-4287-89c2-6d8139fb4343/resource/e5f90e3a-8f8d-4895-9c56-4bb2f7877920/download/vendastesourodireto.csv}") String url,
                              @Value("${primevest.data.dir:../carga/data}") String dataDir,
                              @Value("${tesouro.timeout-seconds:180}") long timeoutSeconds) {
        this(URI.create(url), Path.of(dataDir, "tesouro", FILE_NAME), new PublicFileCache(Duration.ofSeconds(timeoutSeconds)));
    }

    TesouroSalesClient(URI url, Path target, PublicFileCache files) {
        this.url = url;
        this.target = target.toAbsolutePath().normalize();
        this.files = files;
    }

    /** Pergunta ao Tesouro (baixa so se mudou) e conta o que aconteceu, com o arquivo em disco, se houver. */
    public PublicFileCache.Result fetch() {
        return files.get(url, target, true, PublicFileCache.ContentCheck.csvHeader(HEADER_PREFIX));
    }

    /**
     * Titulos com venda (quantidade &gt; 0) nos {@link #RECENT_DAYS} ultimos dias de liquidacao; linha mal formada e
     * pulada; arquivo sem o cabecalho do Tesouro e erro (R6).
     */
    public static OnSale onSale(Path file) {
        Map<LocalDate, Set<Title>> byDate = new HashMap<>();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            TesouroPriceClient.checkHeader(file, in.readLine());
            String line;
            while ((line = in.readLine()) != null) {
                String[] c = line.split(";", -1);
                if (c.length < 5) {
                    continue;
                }
                try {
                    BigDecimal qty = new BigDecimal(c[4].trim().replace(".", "").replace(',', '.'));
                    Title title = new Title(c[0].trim(), LocalDate.parse(c[1].trim(), BR));
                    // o dia conta mesmo sem venda: os "ultimos dias" sao os do arquivo, nao os de cada titulo
                    Set<Title> day = byDate.computeIfAbsent(LocalDate.parse(c[2].trim(), BR), d -> new HashSet<>());
                    if (qty.signum() > 0) {
                        day.add(title);
                    }
                } catch (RuntimeException e) {
                    // linha mal formada
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo de vendas do Tesouro ilegivel: " + e.getMessage(), e);
        }
        TreeSet<LocalDate> dates = new TreeSet<>(byDate.keySet());
        Set<Title> titles = new HashSet<>();
        LocalDate d = dates.isEmpty() ? null : dates.last();
        for (int i = 0; i < RECENT_DAYS && d != null; i++, d = dates.lower(d)) {
            titles.addAll(byDate.get(d));
        }
        return new OnSale(dates.isEmpty() ? null : dates.last(), Set.copyOf(titles));
    }
}