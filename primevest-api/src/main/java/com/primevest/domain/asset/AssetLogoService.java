package com.primevest.domain.asset;

import com.primevest.integration.coingecko.CoinGeckoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Logos dos ativos (PLANO_BRAPI.md, F7, B17, B18 e B25).
 *
 * <p><b>Nao aponta para o site de terceiros na tela.</b> Metade dos icones da
 * BRAPI nao existe (5PRE11.svg da 404) e o endereco da CoinGecko muda a cada
 * troca de imagem. A rotina baixa cada logo uma vez, confere se veio imagem e
 * guarda do nosso lado, em {@code data/logos/} — pasta fora do banco, montada
 * no container da API (B18). {@code assets.logo_path} guarda o nome do arquivo;
 * nulo = sem logo, e a tela mostra as iniciais.</p>
 *
 * <p>Fontes: BRAPI ({@code icons.brapi.dev/icons/{TICKER}.svg}) para acao,
 * FII, ETF (o logo e o da gestora) e BDR — o fracionario usa o do papel
 * inteiro; CoinGecko para cripto.</p>
 *
 * <p>Quando: uma vez por mes (tudo) e na subida da API (so ativo novo, o que
 * nunca foi procurado — {@code logo_checked_at} nulo). B25.</p>
 */
@Slf4j
@Service
public class AssetLogoService {

    private static final Set<String> TYPES_WITH_LOGO = Set.of("ACAO", "FII", "ETF", "BDR", "CRIPTO");
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int PARALLEL = 6;
    private static final Map<String, MediaType> TYPES_BY_EXTENSION = Map.of(
            "svg", MediaType.valueOf("image/svg+xml"),
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "webp", MediaType.valueOf("image/webp"));

    private final AssetRepository assetRepository;
    private final CoinGeckoClient coinGeckoClient;
    private final WebClient webClient;
    private final TransactionTemplate tx;
    private final Path dir;
    private final String brapiIconsUrl;

    @Autowired
    public AssetLogoService(AssetRepository assetRepository, CoinGeckoClient coinGeckoClient,
                            WebClient.Builder builder, PlatformTransactionManager transactionManager,
                            @Value("${primevest.logos.dir:data/logos}") String dir,
                            @Value("${primevest.logos.brapi-icons-url:https://icons.brapi.dev/icons/}") String brapiIconsUrl) {
        this.assetRepository = assetRepository;
        this.coinGeckoClient = coinGeckoClient;
        this.webClient = builder.clone()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_BYTES * 2))
                .build();
        this.tx = new TransactionTemplate(transactionManager);
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        this.brapiIconsUrl = brapiIconsUrl.endsWith("/") ? brapiIconsUrl : brapiIconsUrl + "/";
    }

    public record SyncResult(int checked, int found) {
    }

    /** O tipo tem logo (renda fixa, Tesouro e fundos nao tem). */
    public static boolean typeHasLogo(AssetType type) {
        return type != null && TYPES_WITH_LOGO.contains(type.getName());
    }

    /**
     * Procura e guarda os logos.
     *
     * @param onlyNew so os ativos nunca procurados (subida da API)
     */
    public synchronized SyncResult sync(boolean onlyNew, String reason) {
        List<Asset> assets = assetRepository.findAllWithType().stream()
                .filter(a -> a.isActive() && typeHasLogo(a.getAssetType()))
                .filter(a -> !onlyNew || a.getLogoCheckedAt() == null)
                .toList();
        if (assets.isEmpty()) {
            log.debug("Logos ({}): nada a procurar", reason);
            return new SyncResult(0, 0);
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Logos: nao foi possivel criar a pasta {}: {}", dir, e.getMessage());
            return new SyncResult(0, 0);
        }
        log.info("Logos ({}): procurando {} ativos em {}", reason, assets.size(), dir);

        Map<String, String> coinImages = new HashMap<>();
        List<String> coinIds = assets.stream().filter(a -> "CRIPTO".equals(a.getAssetType().getName()))
                .map(Asset::getPriceSourceRef).filter(id -> id != null && !id.isBlank()).toList();
        if (!coinIds.isEmpty()) {
            try {
                coinImages.putAll(coinGeckoClient.fetchImageUrls(coinIds));
            } catch (RuntimeException e) {
                log.warn("Logos: CoinGecko indisponivel, criptos ficam para a proxima rodada: {}", e.getMessage());
            }
        }

        Semaphore slots = new Semaphore(PARALLEL);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Asset asset : assets) {
                boolean crypto = "CRIPTO".equals(asset.getAssetType().getName());
                if (crypto && coinImages.isEmpty() && !coinIds.isEmpty()) {
                    continue; // CoinGecko fora: nao marca como procurado
                }
                String url = crypto
                        ? coinImages.get(asset.getPriceSourceRef())
                        : brapiIconsUrl + baseTicker(asset.getTicker()) + ".svg";
                results.add(pool.submit(() -> {
                    slots.acquire();
                    try {
                        return fetchAndStore(asset, url);
                    } finally {
                        slots.release();
                    }
                }));
            }
        }
        int found = 0;
        for (Future<Boolean> f : results) {
            try {
                if (Boolean.TRUE.equals(f.get())) {
                    found++;
                }
            } catch (Exception ignored) {
                // cada tarefa ja registra o proprio erro
            }
        }
        log.info("Logos ({}) concluido: {} procurados, {} com logo", reason, results.size(), found);
        return new SyncResult(results.size(), found);
    }

    /** Baixa, confere e grava um logo. Devolve se o ativo ficou com logo. */
    private boolean fetchAndStore(Asset asset, String url) {
        String fileName = null;
        if (url != null) {
            try {
                // 404 e o "nao tem logo" da BRAPI: resposta vazia, sem excecao.
                ResponseEntity<byte[]> resp = webClient.get().uri(url)
                        .exchangeToMono(r -> r.statusCode().is2xxSuccessful()
                                ? r.toEntity(byte[].class)
                                : r.releaseBody().then(reactor.core.publisher.Mono.<ResponseEntity<byte[]>>empty()))
                        .timeout(Duration.ofSeconds(20))
                        .block();
                if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    String ext = extension(resp.getHeaders().getContentType(), resp.getBody());
                    if (ext != null && resp.getBody().length <= MAX_BYTES) {
                        fileName = safeName(asset.getTicker()) + "." + ext;
                        Path target = dir.resolve(fileName);
                        Path temp = dir.resolve(fileName + ".tmp");
                        Files.write(temp, resp.getBody());
                        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (Exception e) {
                log.debug("Logos: {} sem logo em {}: {}", asset.getTicker(), url, e.getMessage());
                fileName = null;
            }
        }
        String stored = fileName;
        String previous = asset.getLogoPath();
        tx.executeWithoutResult(s -> assetRepository.findById(asset.getId()).ifPresent(db -> {
            db.setLogoPath(stored);
            db.setLogoCheckedAt(LocalDateTime.now());
            assetRepository.save(db);
        }));
        if (previous != null && !previous.equals(stored)) {
            try {
                Files.deleteIfExists(dir.resolve(previous));
            } catch (IOException ignored) {
                // arquivo antigo que sobrou nao atrapalha
            }
        }
        return stored != null;
    }

    /** O arquivo do logo de um ativo, para a API servir. */
    public Optional<LogoFile> find(String ticker) {
        return assetRepository.findByTicker(ticker)
                .map(Asset::getLogoPath)
                .map(dir::resolve)
                .filter(p -> p.normalize().startsWith(dir) && Files.isRegularFile(p))
                .map(p -> {
                    String name = p.getFileName().toString();
                    String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                    return new LogoFile(p, TYPES_BY_EXTENSION.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM));
                });
    }

    public record LogoFile(Path path, MediaType mediaType) {
    }

    /** Extensao pelo tipo da resposta; nulo = nao e imagem (pagina de erro, JSON...). */
    static String extension(MediaType type, byte[] body) {
        if (body == null || body.length < 50) {
            return null;
        }
        String head = new String(body, 0, Math.min(body.length, 512), StandardCharsets.UTF_8).toLowerCase();
        if (type != null && type.getSubtype().contains("svg") || head.contains("<svg")) {
            return head.contains("<svg") ? "svg" : null;
        }
        if (type == null || !"image".equals(type.getType())) {
            return null;
        }
        return switch (type.getSubtype()) {
            case "png" -> "png";
            case "jpeg", "jpg" -> "jpg";
            case "webp" -> "webp";
            default -> null;
        };
    }

    /** PETR4F → PETR4: o fracionario tem o logo do papel inteiro. */
    static String baseTicker(String ticker) {
        String t = ticker.trim().toUpperCase();
        return t.endsWith("F") && t.length() > 5 && Character.isDigit(t.charAt(t.length() - 2))
                ? t.substring(0, t.length() - 1) : t;
    }

    private static String safeName(String ticker) {
        return ticker.trim().toUpperCase().replaceAll("[^A-Z0-9._-]", "_");
    }
}
