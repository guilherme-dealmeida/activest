package com.activest.adm.integration.cvm;

import com.activest.adm.integration.PublicFileCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Arquivos dos fundos de investimento nos dados abertos da CVM, com cache em {@code carga/data/cvm/fi}
 * (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, 11.4), no padrão de nomes da CVM:
 *
 * <ul>
 *   <li>{@code fi/registro_fundo_classe.zip}, {@code fi/cad_fi_hist.zip} e {@code fi/cad_fi.csv}: cadastros, reescritos
 *   pela CVM todo dia;</li>
 *   <li>{@code fi/inf_diario/inf_diario_fi_AAAAMM.zip}: informe diário mensal (2021 em diante);</li>
 *   <li>{@code fi/inf_diario/hist/inf_diario_fi_AAAA.zip}: informe diário anual (até 2020, {@code HIST/} na CVM).</li>
 * </ul>
 *
 * <p>A CVM regera os últimos 12 meses (retificações): o mês corrente e os 12 anteriores perguntam com
 * {@code If-Modified-Since} (D9); os mais antigos e os anuais ficam no disco.</p>
 */
@Slf4j
@Component
public class CvmFundFiles {

    static final String REGISTRY = "FI/CAD/DADOS/registro_fundo_classe.zip";
    static final String REGISTRY_HISTORY = "FI/CAD/DADOS/cad_fi_hist.zip";
    static final String OLD_REGISTRY = "FI/CAD/DADOS/cad_fi.csv";
    static final String DAILY = "FI/DOC/INF_DIARIO/DADOS/";
    /** Primeiro mês publicado em arquivo mensal; antes, só o anual de HIST/. */
    public static final YearMonth FIRST_MONTHLY = YearMonth.of(2021, 1);
    public static final int FIRST_YEAR = 2010;

    private static final Pattern MONTHLY_NAME = Pattern.compile("inf_diario_fi_(\\d{6})\\.zip");
    private static final Pattern YEARLY_NAME = Pattern.compile("inf_diario_fi_(\\d{4})\\.zip");

    private final PublicFileCache files;
    private final String baseUrl;
    private final Path dir;

    @Autowired
    public CvmFundFiles(@Value("${cvm.base-url:https://dados.cvm.gov.br/dados}") String baseUrl,
                        @Value("${activest.data.dir:../carga/data}") String dataDir,
                        @Value("${cvm.timeout-seconds:120}") long timeoutSeconds) {
        this(baseUrl, Path.of(dataDir, "cvm", "fi"), Duration.ofSeconds(timeoutSeconds));
    }

    public CvmFundFiles(String baseUrl, Path dir, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.dir = dir.toAbsolutePath().normalize();
        this.files = new PublicFileCache(timeout);
    }

    /** {@code carga/data/cvm/fi}. */
    public Path dir() {
        return dir;
    }

    public Path dailyDir() {
        return dir.resolve("inf_diario");
    }

    public PublicFileCache.Result registry(boolean refresh) {
        return files.get(URI.create(baseUrl + REGISTRY), dir.resolve("registro_fundo_classe.zip"), refresh,
                PublicFileCache.ContentCheck.zip());
    }

    public PublicFileCache.Result registryHistory(boolean refresh) {
        return files.get(URI.create(baseUrl + REGISTRY_HISTORY), dir.resolve("cad_fi_hist.zip"), refresh,
                PublicFileCache.ContentCheck.zip());
    }

    public PublicFileCache.Result oldRegistry(boolean refresh) {
        return files.get(URI.create(baseUrl + OLD_REGISTRY), dir.resolve("cad_fi.csv"), refresh,
                PublicFileCache.ContentCheck.csvHeader("TP_FUNDO;"));
    }

    /** Informe mensal; {@code refresh} pergunta à CVM se mudou (mês corrente e janela de 12 meses). */
    public PublicFileCache.Result monthly(YearMonth month, boolean refresh) {
        String name = "inf_diario_fi_" + month.getYear() + String.format("%02d", month.getMonthValue()) + ".zip";
        return files.get(URI.create(baseUrl + DAILY + name), dailyDir().resolve(name), refresh,
                PublicFileCache.ContentCheck.zip());
    }

    /** Informe anual de {@code HIST/} (até 2020): não muda mais, o disco basta. */
    public PublicFileCache.Result yearly(int year) {
        String name = "inf_diario_fi_" + year + ".zip";
        return files.get(URI.create(baseUrl + DAILY + "HIST/" + name), dailyDir().resolve("hist").resolve(name), false,
                PublicFileCache.ContentCheck.zip());
    }

    /** Os ZIPs do informe em disco, do mais antigo ao mais novo: os anuais de hist/ e depois os mensais. */
    public List<Path> localDailyFiles() {
        List<Path> out = new ArrayList<>();
        out.addAll(list(dailyDir().resolve("hist"), YEARLY_NAME));
        out.addAll(list(dailyDir(), MONTHLY_NAME));
        return out;
    }

    /** O mês de um ZIP mensal, ou nulo (anual). */
    public static YearMonth monthOf(Path zip) {
        Matcher m = MONTHLY_NAME.matcher(zip.getFileName().toString());
        if (!m.matches()) {
            return null;
        }
        String s = m.group(1);
        return YearMonth.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4)));
    }

    /** O ano de um ZIP anual, ou nulo (mensal). */
    public static Integer yearOf(Path zip) {
        Matcher m = YEARLY_NAME.matcher(zip.getFileName().toString());
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    private static List<Path> list(Path folder, Pattern name) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> name.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
