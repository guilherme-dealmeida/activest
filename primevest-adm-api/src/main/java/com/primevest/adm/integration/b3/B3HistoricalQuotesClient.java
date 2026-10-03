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
 * {@code data/b3/cotahist} (PLANO_EMPRESAS_BALANCOS.md, F2).
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
            @Value("${primevest.data.dir:../data}") String dataDir,
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
