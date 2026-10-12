package com.primevest.adm.integration.b3;

import com.primevest.adm.integration.PublicFileCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Serie historica de cotacoes da B3 (COTAHIST), um zip por ano, com cache em
 * {@code carga/data/b3/cotahist} (docs/planos/PLANO_EMPRESAS_BALANCOS.md, F2).
 *
 * <p>Ano fechado em disco: sem rede. Ano corrente e anterior: pergunta se mudou
 * (o zip do ano corrente cresce a cada pregao).</p>
 */
@Component
public class B3HistoricalQuotesClient {

    private final PublicFileCache files;
    private final String baseUrl;
    private final Path dir;
    private final Clock clock;

    @Autowired
    public B3HistoricalQuotesClient(
            @Value("${b3.cotahist-url:https://bvmf.bmfbovespa.com.br/InstDados/SerHist}") String baseUrl,
            @Value("${primevest.data.dir:../carga/data}") String dataDir,
            @Value("${b3.cotahist-timeout-seconds:300}") long timeoutSeconds) {
        this(baseUrl, Path.of(dataDir, "b3", "cotahist"), Duration.ofSeconds(timeoutSeconds),
                Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    B3HistoricalQuotesClient(String baseUrl, Path dir, Duration timeout, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.dir = dir.toAbsolutePath().normalize();
        this.files = new PublicFileCache(timeout);
        this.clock = clock;
    }

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy");

    /**
     * Zip de um pregao ({@code COTAHIST_DddMMaaaa.ZIP}, ~0,5 MB, todas as acoes e FIIs), com cache em
     * {@code carga/data/b3/cotahist/diario} (docs/planos/PLANO_PRECO_TETO.md, 4.5). O arquivo do dia nao muda
     * depois de publicado: em disco, sem rede. Vazio quando a B3 responde 404 — dia sem pregao, ou o arquivo do
     * dia ainda nao saiu.
     */
    public Optional<Path> day(LocalDate date) {
        String name = "COTAHIST_D" + date.format(DAY) + ".ZIP";
        try {
            return files.fetch(URI.create(baseUrl + name), dir.resolve("diario").resolve(name), false);
        } catch (PublicFileCache.UnavailableException e) {
            throw new B3Exception("Cotacoes do pregao de " + date + " indisponiveis na B3: " + e.getMessage(), e);
        }
    }

    /** Zip do ano. Vazio quando a B3 nao tem o ano e nao ha copia em disco. */
    public Optional<Path> year(int year) {
        String name = "COTAHIST_A" + year + ".ZIP";
        int current = LocalDate.now(clock).getYear();
        try {
            return files.fetch(URI.create(baseUrl + name), dir.resolve(name), year >= current - 1);
        } catch (PublicFileCache.UnavailableException e) {
            throw new B3Exception("Serie historica da B3 indisponivel para " + year + ": " + e.getMessage(), e);
        }
    }
}
