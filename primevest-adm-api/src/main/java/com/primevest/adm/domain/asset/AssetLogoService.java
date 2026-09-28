package com.primevest.adm.domain.asset;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetLogos;
import com.primevest.domain.asset.AssetRepository;

import com.primevest.adm.integration.coingecko.CoinGeckoClient;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Logos dos ativos (PLANO_BRAPI.md, F7, B17, B18 e B25; PLANO_EMPRESAS_BALANCOS.md, F10).
 *
 * <p><b>Nao aponta para o site de terceiros na tela.</b> Metade dos icones da
 * BRAPI nao existe (5PRE11.svg da 404) e o endereco da CoinGecko muda a cada
 * troca de imagem. A rotina baixa cada logo, confere se veio imagem e guarda do
 * nosso lado, em {@code data/logos/{TICKER}.{ext}} — pasta fora do banco, montada
 * no container da API (B18).</p>
 *
 * <p><b>O arquivo e o estado</b> (F10): nada no catalogo diz se o ativo tem logo ou
 * se ja foi procurado — {@link AssetLogos} indexa a pasta. Busca sem resultado nao
 * apaga o arquivo que ja existe (a BRAPI tem dias de 404); troca de logo apaga o
 * arquivo antigo do mesmo ticker com outra extensao.</p>
 *
 * <p>Fontes: BRAPI ({@code icons.brapi.dev/icons/{TICKER}.svg}) para acao,
 * FII, ETF (o logo e o da gestora) e BDR — o fracionario usa o do papel
 * inteiro; CoinGecko para cripto.</p>
 *
 * <p>Quando: uma vez por mes, so quem nao tem arquivo; tudo de novo sob demanda
 * ({@code POST /admin/logos}); o ativo de uma transacao, se nao tiver arquivo
 * (pedido "preciso do historico" da carteira). A subida da API nao procura nada.</p>
 */
@Slf4j
@Service
public class AssetLogoService {

    private static final int MAX_BYTES = 1024 * 1024;
    private static final int PARALLEL = 6;

    private final AssetRepository assetRepository;
    private final AssetLogos logos;
    private final CoinGeckoClient coinGeckoClient;
    private final WebClient webClient;
    private final TransactionTemplate tx;
    private final Path dir;
    private final String brapiIconsUrl;

    @Autowired
    public AssetLogoService(AssetRepository assetRepository, AssetLogos logos, CoinGeckoClient coinGeckoClient,
                            WebClient.Builder builder, PlatformTransactionManager transactionManager,
                            @Value("${primevest.logos.brapi-icons-url:https://icons.brapi.dev/icons/}") String brapiIconsUrl) {
        this.assetRepository = assetRepository;
        this.logos = logos;
        this.coinGeckoClient = coinGeckoClient;
        this.webClient = builder.clone()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_BYTES * 2))
                .build();
        this.tx = new TransactionTemplate(transactionManager);
        this.dir = logos.dir();
        this.brapiIconsUrl = brapiIconsUrl.endsWith("/") ? brapiIconsUrl : brapiIconsUrl + "/";
    }

    public record SyncResult(int checked, int found) {
    }

    /**
     * Procura e guarda os logos.
     *
     * @param onlyMissing so os ativos sem arquivo em {@code data/logos} (rotina mensal)
     */
    public synchronized SyncResult sync(boolean onlyMissing, String reason) {
        logos.reindex();
        List<Asset> assets = assetRepository.findAllWithType().stream()
                .filter(a -> a.isActive() && AssetLogos.typeHasLogo(a.getAssetType()))
                .filter(a -> !onlyMissing || !logos.hasLogo(a.getTicker()))
                .toList();
        return fetch(assets, reason);
    }

    /**
     * Um ativo, se ainda nao tem arquivo (ativo novo que entrou numa carteira). Nao espera:
     * o download roda numa thread propria.
     */
    public void fetchIfMissing(Long assetId) {
        Asset asset = tx.execute(s -> assetRepository.findById(assetId).map(a -> {
            a.getAssetType().getName();
            return a;
        }).orElse(null));
        if (asset == null || !AssetLogos.typeHasLogo(asset.getAssetType()) || logos.hasLogo(asset.getTicker())) {
            return;
        }
        Thread.ofVirtual().name("logo-" + asset.getTicker()).start(() -> {
            try {
                fetch(List.of(asset), "ativo novo " + asset.getTicker());
            } catch (RuntimeException e) {
                log.warn("Logos: {} nao procurado agora: {}", asset.getTicker(), e.getMessage());
            }
        });
    }

    private SyncResult fetch(List<Asset> assets, String reason) {
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
                String url = crypto
                        ? coinImages.get(asset.getPriceSourceRef())
                        : brapiIconsUrl + baseTicker(asset.getTicker()) + ".svg";
                if (url == null) {
                    continue;
                }
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
        log.info("Logos ({}) concluido: {} procurados, {} com logo gravado", reason, results.size(), found);
        return new SyncResult(results.size(), found);
    }

    /** Baixa, confere e grava um logo. Devolve se gravou. */
    private boolean fetchAndStore(Asset asset, String url) {
        try {
            // 404 e o "nao tem logo" da BRAPI: resposta vazia, sem excecao.
            ResponseEntity<byte[]> resp = webClient.get().uri(url)
                    .exchangeToMono(r -> r.statusCode().is2xxSuccessful()
                            ? r.toEntity(byte[].class)
                            : r.releaseBody().then(reactor.core.publisher.Mono.<ResponseEntity<byte[]>>empty()))
                    .timeout(Duration.ofSeconds(20))
                    .block();
            if (resp == null || !resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return false;
            }
            String ext = extension(resp.getHeaders().getContentType(), resp.getBody());
            if (ext == null || resp.getBody().length > MAX_BYTES) {
                return false;
            }
            String fileName = AssetLogos.fileStem(asset.getTicker()) + "." + ext;
            Path target = dir.resolve(fileName);
            Path temp = dir.resolve(fileName + ".tmp");
            Files.write(temp, resp.getBody());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            for (Path old : logos.otherFiles(asset.getTicker(), fileName)) {
                Files.deleteIfExists(old);
            }
            logos.stored(asset.getTicker(), fileName);
            return true;
        } catch (Exception e) {
            log.debug("Logos: {} sem logo em {}: {}", asset.getTicker(), url, e.getMessage());
            return false;
        }
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
}
