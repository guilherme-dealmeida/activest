package com.primevest.adm.integration.cvm;

import com.primevest.adm.integration.PublicFileCache;
import lombok.extern.slf4j.Slf4j;
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
 * Arquivos dos dados abertos da CVM ({@code dados.cvm.gov.br}), com cache em
 * disco em {@code carga/data/cvm} (docs/planos/PLANO_EMPRESAS_BALANCOS.md, secao 2).
 *
 * <p><b>O arquivo e o estado</b> ({@link PublicFileCache}). Ano fechado em
 * disco: usa o local, sem rede. Ano corrente ou anterior (a CVM ainda recebe
 * entregas e reapresentacoes): pergunta com {@code If-Modified-Since} e so baixa
 * se mudou. Fonte fora do ar: fica com o que ha em disco.</p>
 */
@Slf4j
@Component
public class CvmOpenDataClient {

    static final String COMPANY_REGISTRY = "CIA_ABERTA/CAD/DADOS/cad_cia_aberta.csv";
    static final String INTERMEDIARY_REGISTRY = "INTERMED/CAD/DADOS/cad_intermed.zip";
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final PublicFileCache files;
    private final String baseUrl;
    private final Path dir;
    private final Clock clock;

    @Autowired
    public CvmOpenDataClient(@Value("${cvm.base-url:https://dados.cvm.gov.br/dados}") String baseUrl,
                             @Value("${primevest.data.dir:../carga/data}") String dataDir,
                             @Value("${cvm.timeout-seconds:120}") long timeoutSeconds) {
        this(baseUrl, Path.of(dataDir, "cvm"), Duration.ofSeconds(timeoutSeconds), Clock.system(ZONE));
    }

    CvmOpenDataClient(String baseUrl, Path dir, Duration timeout, Clock clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.dir = dir.toAbsolutePath().normalize();
        this.clock = clock;
        this.files = new PublicFileCache(timeout);
    }

    /** Pasta do cache ({@code carga/data/cvm}). */
    public Path dir() {
        return dir;
    }

    /**
     * Cadastro de companhias abertas. Arquivo unico, reescrito todo dia pela CVM:
     * sempre pergunta se mudou.
     */
    public Path companyRegistry() {
        Path target = dir.resolve("cad").resolve("cad_cia_aberta.csv");
        return fetch(COMPANY_REGISTRY, target, true)
                .orElseThrow(() -> new CvmException("Cadastro de companhias da CVM indisponivel e sem copia em disco"));
    }

    /**
     * Cadastro de participantes intermediarios (corretoras, distribuidoras, bancos com carteira de investimento...),
     * vivos e cancelados (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 4.1). Zip unico, reescrito pela CVM: sempre
     * pergunta se mudou.
     */
    public Path intermediaryRegistry() {
        Path target = dir.resolve("cad").resolve("cad_intermed.zip");
        return fetch(INTERMEDIARY_REGISTRY, target, true)
                .orElseThrow(() -> new CvmException("Cadastro de intermediarios da CVM indisponivel e sem copia em disco"));
    }

    /**
     * PDF de um documento do RAD (link do indice IPE), em {@code carga/data/cvm/proventos/{protocolo}_{versao}.pdf}.
     * Documento entregue nao muda (reapresentacao e outra versao): com o arquivo em disco, nao vai a
     * rede. Vazio quando o RAD responde 404.
     */
    public Optional<Path> radDocument(String link, long protocol, int version) {
        Path target = dir.resolve("proventos").resolve(protocol + "_" + version + ".pdf");
        try {
            return files.fetch(URI.create(link), target, false);
        } catch (PublicFileCache.UnavailableException e) {
            throw new CvmException("RAD indisponivel para o protocolo " + protocol + ": " + e.getMessage(), e);
        }
    }

    /** Zip anual de um conjunto. Vazio quando o ano nao existe na CVM e nao ha copia em disco. */
    public Optional<Path> yearly(CvmDataset dataset, int year) {
        Path target = dir.resolve(dataset.folder()).resolve(dataset.fileName(year));
        int current = LocalDate.now(clock).getYear();
        return fetch(dataset.remotePath(year), target, year >= current - 1);
    }

    private Optional<Path> fetch(String remotePath, Path target, boolean refresh) {
        try {
            return files.fetch(URI.create(baseUrl + remotePath), target, refresh);
        } catch (PublicFileCache.UnavailableException e) {
            throw new CvmException("CVM indisponivel para " + remotePath + ": " + e.getMessage(), e);
        }
    }
}
