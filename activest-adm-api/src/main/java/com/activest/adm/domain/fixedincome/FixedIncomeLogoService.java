package com.activest.adm.domain.fixedincome;

import com.activest.domain.task.TaskRun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.adm.domain.asset.AssetLogoService;
import com.activest.domain.fixedincome.FixedIncomeGroupRepository;
import com.activest.domain.fixedincome.FixedIncomeIssuerRepository;
import com.activest.domain.fixedincome.FixedIncomeLogos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logos das emissoras e distribuidoras de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, Adendo A).
 *
 * <p>Para cada raiz de CNPJ do cadastro (e cada lider de grupo), a primeira fonte que tiver logo:</p>
 * <ol>
 *   <li>o diretorio de participantes do Open Finance Brasil — a propria instituicao publica o logo
 *       ({@code AuthorisationServers[].CustomerFriendlyLogoUri}), pelo CNPJ;</li>
 *   <li>o pacote {@code logos-bancos-br} (listas do BCB + o diretorio acima), pelo ISPB, que e a raiz do CNPJ — o PNG
 *       256x256 que o pacote normaliza, numa versao fixa na configuracao.</li>
 * </ol>
 *
 * <p>Grava em {@code carga/data/logos/rf/{raiz}.{svg|png}} ({@link FixedIncomeLogos}): o arquivo e o estado, como nos
 * logos do catalogo. Busca sem resultado nao apaga o que ja existe; fonte fora do ar nao falha a rotina (a outra
 * continua). SVG com script nao entra (A3).</p>
 */
@Slf4j
@Service
public class FixedIncomeLogoService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Pattern FILE_STEM = Pattern.compile("/(\\d{8})\\.(?:svg|png)$");
    /** O que nao pode haver num SVG servido do nosso dominio (A3). */
    private static final Pattern UNSAFE_SVG = Pattern.compile(
        "<script|<foreignobject|<iframe|<embed|<object|javascript:|\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    private final FixedIncomeIssuerRepository issuers;
    private final FixedIncomeGroupRepository groups;
    private final FixedIncomeLogos logos;
    private final WebClient webClient;
    private final String openFinanceUrl;
    private final String logosBancosBrUrl;

    @Autowired
    public FixedIncomeLogoService(FixedIncomeIssuerRepository issuers, FixedIncomeGroupRepository groups,
                                  FixedIncomeLogos logos, WebClient.Builder builder,
                                  @Value("${activest.fixed-income-logos.open-finance-url:"
                                      + "https://data.directory.openbankingbrasil.org.br/participants}")
                                  String openFinanceUrl,
                                  @Value("${activest.fixed-income-logos.logos-bancos-br-url:"
                                      + "https://cdn.jsdelivr.net/npm/logos-bancos-br@0.8.0/}")
                                  String logosBancosBrUrl) {
        this.issuers = issuers;
        this.groups = groups;
        this.logos = logos;
        this.webClient = builder.clone()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
        this.openFinanceUrl = openFinanceUrl;
        this.logosBancosBrUrl = logosBancosBrUrl.endsWith("/") ? logosBancosBrUrl : logosBancosBrUrl + "/";
    }

    /**
     * @param candidates raizes procuradas
     * @param openFinance gravadas do Open Finance
     * @param logosBancosBr gravadas do logos-bancos-br
     */
    public record Result(int candidates, int openFinance, int logosBancosBr) {
    }

    /**
     * Procura e guarda os logos de renda fixa.
     *
     * @param onlyMissing so as raizes sem arquivo (rotina mensal); {@code false} baixa de novo quem as fontes tiverem
     */
    public synchronized Result sync(boolean onlyMissing, String reason) {
        TreeSet<String> roots = new TreeSet<>(issuers.findAllCnpjRoots());
        roots.addAll(groups.findAllLeaderRoots());
        return syncRoots(roots, onlyMissing, reason);
    }

    Result syncRoots(Collection<String> roots, boolean onlyMissing, String reason) {
        logos.reindex();
        try {
            Files.createDirectories(logos.dir());
        } catch (IOException e) {
            log.error("Logos de renda fixa: nao foi possivel criar a pasta {}: {}", logos.dir(), e.getMessage());
            return new Result(0, 0, 0);
        }
        Map<String, String> openFinance = load("Open Finance", () -> openFinanceLogos(download(openFinanceUrl)));
        Map<String, String> bancosBr = load("logos-bancos-br",
            () -> logosBancosBr(download(logosBancosBrUrl + "data/logo-urls.min.json"), logosBancosBrUrl));

        int candidates = 0;
        int fromOpenFinance = 0;
        int fromBancosBr = 0;
        for (String root : roots) {
            TaskRun.checkStop(); // ponto de parada (docs/planos/PLANO_TAREFAS.md, D8)
            if (!FixedIncomeLogos.valid(root) || (onlyMissing && logos.hasLogo(root))) {
                continue;
            }
            String of = openFinance.get(root);
            String bb = bancosBr.get(root);
            if (of == null && bb == null) {
                continue;
            }
            candidates++;
            if (of != null && fetchAndStore(root, of)) {
                fromOpenFinance++;
            } else if (bb != null && fetchAndStore(root, bb)) {
                fromBancosBr++;
            }
        }
        log.info("Logos de renda fixa ({}): {} raizes com logo nas fontes, {} gravadas do Open Finance e {} do "
            + "logos-bancos-br", reason, candidates, fromOpenFinance, fromBancosBr);
        return new Result(candidates, fromOpenFinance, fromBancosBr);
    }

    /**
     * Diretorio do Open Finance: raiz do CNPJ → URL do logo. A organizacao pode ter varios servidores de autorizacao;
     * vale o primeiro com logo.
     */
    static Map<String, String> openFinanceLogos(byte[] body) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode org : JSON.readTree(body)) {
            String cnpj = org.path("RegistrationNumber").asText("").replaceAll("\\D", "");
            if (cnpj.length() < 8) {
                continue;
            }
            for (JsonNode server : org.path("AuthorisationServers")) {
                String uri = server.path("CustomerFriendlyLogoUri").asText("").trim();
                if (uri.startsWith("https://") || uri.startsWith("http://")) {
                    out.putIfAbsent(cnpj.substring(0, 8), uri);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * {@code data/logo-urls.min.json} do logos-bancos-br: ISPB → URL do SVG. Vale o PNG normalizado do mesmo arquivo,
     * na versao fixa da configuracao (a URL do indice aponta para a versao "@0", que muda sozinha).
     */
    static Map<String, String> logosBancosBr(byte[] body, String baseUrl) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode root = JSON.readTree(body);
        root.fields().forEachRemaining(e -> {
            Matcher m = FILE_STEM.matcher(e.getValue().asText(""));
            if (FixedIncomeLogos.valid(e.getKey()) && m.find()) {
                out.put(e.getKey(), baseUrl + "logos/png/" + m.group(1) + ".png");
            }
        });
        return out;
    }

    /** SVG que pode ser servido do nosso dominio: sem script, evento ou conteudo embutido (A3). */
    static boolean safeSvg(byte[] body) {
        return !UNSAFE_SVG.matcher(new String(body, StandardCharsets.UTF_8)).find();
    }

    /**
     * O PNG normalizado do logos-bancos-br vem com margem transparente em volta da marca; na tela, ao lado dos logos
     * do catalogo (que ocupam o quadro todo), ele parecia menor. Corta a margem e devolve um quadrado (a marca no
     * centro). Imagem sem transparencia, sem margem ou ilegivel volta como veio.
     */
    static byte[] trimTransparentMargin(byte[] png) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null || !img.getColorModel().hasAlpha()) {
                return png;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            int minX = w;
            int minY = h;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((img.getRGB(x, y) >>> 24) > 16) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < 0 || (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1)) {
                return png;
            }
            int cw = maxX - minX + 1;
            int ch = maxY - minY + 1;
            int side = Math.max(cw, ch);
            java.awt.image.BufferedImage out =
                new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.drawImage(img.getSubimage(minX, minY, cw, ch), (side - cw) / 2, (side - ch) / 2, null);
            g.dispose();
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException | RuntimeException e) {
            return png;
        }
    }

    private interface Loader {
        Map<String, String> get() throws IOException;
    }

    private Map<String, String> load(String source, Loader loader) {
        try {
            Map<String, String> map = loader.get();
            log.info("Logos de renda fixa: {} com {} raizes", source, map.size());
            return map;
        } catch (Exception e) {
            log.warn("Logos de renda fixa: {} indisponivel, segue sem ele: {}", source, e.getMessage());
            return Map.of();
        }
    }

    private byte[] download(String url) throws IOException {
        ResponseEntity<byte[]> resp = webClient.get().uri(java.net.URI.create(url))
            .exchangeToMono(r -> r.statusCode().is2xxSuccessful()
                ? r.toEntity(byte[].class)
                : r.releaseBody().then(Mono.<ResponseEntity<byte[]>>empty()))
            .timeout(Duration.ofSeconds(60))
            .block();
        if (resp == null || resp.getBody() == null) {
            throw new IOException("sem resposta de " + url);
        }
        return resp.getBody();
    }

    /** Baixa, confere e grava um logo. Devolve se gravou. */
    private boolean fetchAndStore(String root, String url) {
        try {
            ResponseEntity<byte[]> resp = webClient.get().uri(java.net.URI.create(url))
                .exchangeToMono(r -> r.statusCode().is2xxSuccessful()
                    ? r.toEntity(byte[].class)
                    : r.releaseBody().then(Mono.<ResponseEntity<byte[]>>empty()))
                .timeout(Duration.ofSeconds(20))
                .block();
            if (resp == null || resp.getBody() == null || resp.getBody().length > MAX_BYTES) {
                return false;
            }
            byte[] body = resp.getBody();
            String ext = AssetLogoService.extension(resp.getHeaders().getContentType(), body);
            if (ext == null || ("svg".equals(ext) && !safeSvg(body))) {
                log.debug("Logos de renda fixa: {} recusado em {} (nao e imagem ou SVG com script)", root, url);
                return false;
            }
            if ("png".equals(ext)) {
                body = trimTransparentMargin(body);
            }
            String fileName = root + "." + ext;
            Path target = logos.dir().resolve(fileName);
            Path temp = logos.dir().resolve(fileName + ".tmp");
            Files.write(temp, body);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            for (Path old : logos.otherFiles(root, fileName)) {
                Files.deleteIfExists(old);
            }
            logos.stored(root, fileName);
            return true;
        } catch (Exception e) {
            log.debug("Logos de renda fixa: {} sem logo em {}: {}", root, url, e.getMessage());
            return false;
        }
    }
}
