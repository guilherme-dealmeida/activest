package com.activest.adm.integration.bcb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.adm.integration.PublicFileCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cadastro de instituicoes do BCB pela API Olinda (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 4.1). Sem token.
 *
 * <ul>
 *   <li><b>IF.data — cadastro</b> ({@code IFDATA/versao/v1/odata/IfDataCadastro(AnoMes=@AnoMes)}): todas as
 *   instituicoes da data-base, <b>vivas e mortas</b>, com segmento, situacao e conglomerado prudencial. Trimestral;
 *   a data-base mais recente e a primeira, das ultimas oito, que vier com linhas. Cache em
 *   {@code carga/data/bcb/ifdata/cadastro_{AnoMes}.json}; data-base em disco nao vai a rede.</li>
 *   <li><b>Instituicoes em funcionamento</b> ({@code Instituicoes_em_funcionamento/versao/v1/odata/Sedes...}): so as
 *   vivas, com o site. Cache em {@code carga/data/bcb/instituicoes/{conjunto}.json}, sempre perguntando se mudou.</li>
 * </ul>
 */
@Slf4j
@Component
public class BcbInstitutionsClient {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SEATS = List.of("SedesBancoComMultCE", "SedesSociedades", "SedesCooperativas");

    private final PublicFileCache files;
    private final String ifdataUrl;
    private final String institutionsUrl;
    private final Path dir;
    private final Clock clock;

    @Autowired
    public BcbInstitutionsClient(
            @Value("${bcb.ifdata-url:https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/odata}") String ifdataUrl,
            @Value("${bcb.institutions-url:https://olinda.bcb.gov.br/olinda/servico/Instituicoes_em_funcionamento/versao/v1/odata}")
            String institutionsUrl,
            @Value("${activest.data.dir:../carga/data}") String dataDir,
            @Value("${bcb.timeout-seconds:120}") long timeoutSeconds) {
        this(ifdataUrl, institutionsUrl, Path.of(dataDir, "bcb"), Duration.ofSeconds(timeoutSeconds),
                Clock.system(ZONE));
    }

    BcbInstitutionsClient(String ifdataUrl, String institutionsUrl, Path dir, Duration timeout, Clock clock) {
        this.ifdataUrl = trim(ifdataUrl);
        this.institutionsUrl = trim(institutionsUrl);
        this.dir = dir.toAbsolutePath().normalize();
        this.clock = clock;
        this.files = new PublicFileCache(timeout);
    }

    /** O cadastro da data-base mais recente: instituicoes e conglomerados. */
    public record Registry(String baseMonth, List<BcbInstitution> institutions, Map<String, BcbConglomerate> groups) {
    }

    public Registry latestRegistry() {
        YearMonth m = YearMonth.from(LocalDate.now(clock));
        // ultimo fim de trimestre (marco, junho, setembro, dezembro) ate hoje
        while (m.getMonthValue() % 3 != 0) {
            m = m.minusMonths(1);
        }
        BcbException last = null;
        for (int i = 0; i < 8; i++, m = m.minusMonths(3)) {
            String am = String.format("%d%02d", m.getYear(), m.getMonthValue());
            try {
                Optional<Registry> r = registry(am);
                if (r.isPresent()) {
                    return r.get();
                }
            } catch (BcbException e) {
                last = e;
                log.warn("BCB IF.data: data-base {} indisponivel: {}", am, e.getMessage());
            }
        }
        throw last != null ? last : new BcbException("BCB IF.data sem cadastro nas ultimas oito data-bases");
    }

    /** O cadastro de uma data-base; vazio quando o BCB ainda nao a publicou. */
    public Optional<Registry> registry(String baseMonth) {
        Path target = dir.resolve("ifdata").resolve("cadastro_" + baseMonth + ".json");
        URI url = URI.create(ifdataUrl + "/IfDataCadastro(AnoMes=@AnoMes)?@AnoMes=" + baseMonth
                + "&$format=json&$top=20000");
        Path file;
        try {
            file = files.fetch(url, target, false, PublicFileCache.ContentCheck.json()).orElse(null);
        } catch (PublicFileCache.UnavailableException e) {
            throw new BcbException("IF.data indisponivel para " + baseMonth + ": " + e.getMessage(), e);
        }
        if (file == null) {
            return Optional.empty();
        }
        Registry r = parseRegistry(baseMonth, read(file));
        if (r.institutions().isEmpty()) {
            // data-base ainda nao publicada: a resposta vazia nao fica no cache
            delete(file);
            return Optional.empty();
        }
        return Optional.of(r);
    }

    /** Site das instituicoes vivas, pela raiz do CNPJ. Fonte fora do ar: o que houver em disco, ou nada. */
    public Map<String, String> websites() {
        Map<String, String> out = new HashMap<>();
        for (String seat : SEATS) {
            Path target = dir.resolve("instituicoes").resolve(seat + ".json");
            URI url = URI.create(institutionsUrl + "/" + seat + "?$format=json&$top=50000");
            try {
                files.fetch(url, target, true, PublicFileCache.ContentCheck.json()).ifPresent(f -> parseWebsites(read(f), out));
            } catch (PublicFileCache.UnavailableException | BcbException e) {
                log.warn("BCB instituicoes em funcionamento ({}): sem site agora: {}", seat, e.getMessage());
            }
        }
        return out;
    }

    public static Registry parseRegistry(String baseMonth, String body) {
        try {
            JsonNode root = JSON.readTree(body).path("value");
            List<BcbInstitution> institutions = new ArrayList<>();
            Map<String, BcbConglomerate> groups = new LinkedHashMap<>();
            for (JsonNode n : root) {
                String code = text(n, "CodInst");
                if (code == null) {
                    continue;
                }
                if (code.startsWith("C")) {
                    groups.put(code, new BcbConglomerate(code, text(n, "NomeInstituicao"),
                            root8(text(n, "CnpjInstituicaoLider"))));
                    continue;
                }
                String cnpjRoot = root8(code);
                if (cnpjRoot == null) {
                    continue;
                }
                institutions.add(new BcbInstitution(cnpjRoot, text(n, "NomeInstituicao"), text(n, "SegmentoTb"),
                        text(n, "Situacao"), text(n, "CodConglomeradoPrudencial"),
                        root8(text(n, "CnpjInstituicaoLider"))));
            }
            return new Registry(baseMonth, institutions, groups);
        } catch (IOException | RuntimeException e) {
            throw new BcbException("Resposta do IF.data ilegivel (" + baseMonth + "): " + e.getMessage(), e);
        }
    }

    public static void parseWebsites(String body, Map<String, String> out) {
        try {
            for (JsonNode n : JSON.readTree(body).path("value")) {
                String root = root8(text(n, "CNPJ"));
                String site = text(n, "SITIO_NA_INTERNET");
                if (root != null && site != null) {
                    out.putIfAbsent(root, site.toLowerCase());
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new BcbException("Resposta de instituicoes em funcionamento ilegivel: " + e.getMessage(), e);
        }
    }

    /** Raiz do CNPJ (8 digitos), com zeros a esquerda: de uma raiz ou de um CNPJ completo. Nula se nao for CNPJ. */
    public static String root8(String raw) {
        if (raw == null || raw.chars().anyMatch(Character::isLetter)) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() > 8) {
            return digits.length() >= 13 && digits.length() <= 14
                    ? ("0".repeat(14 - digits.length()) + digits).substring(0, 8) : null;
        }
        return "0".repeat(8 - digits.length()) + digits;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new BcbException("Arquivo do BCB ilegivel: " + file, e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("BCB: nao consegui apagar {}: {}", file, e.getMessage());
        }
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
