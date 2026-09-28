package com.primevest.domain.asset;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Leitura dos logos ja gravados em {@code data/logos/} (PLANO_BRAPI.md, B18): quais tipos
 * tem logo e qual arquivo servir. Separado do download ({@code AssetLogoService}), que e
 * rotina de ingestao (PLANO_ADM_API.md, 4.3).
 */
@Component
public class AssetLogos {

    private static final Set<String> TYPES_WITH_LOGO = Set.of("ACAO", "FII", "ETF", "BDR", "CRIPTO");
    private static final Map<String, MediaType> TYPES_BY_EXTENSION = Map.of(
            "svg", MediaType.valueOf("image/svg+xml"),
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "webp", MediaType.valueOf("image/webp"));

    private final AssetRepository assetRepository;
    private final Path dir;

    public AssetLogos(AssetRepository assetRepository, @Value("${primevest.logos.dir:data/logos}") String dir) {
        this.assetRepository = assetRepository;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    /** O tipo tem logo (renda fixa, Tesouro e fundos nao tem). */
    public static boolean typeHasLogo(AssetType type) {
        return type != null && TYPES_WITH_LOGO.contains(type.getName());
    }

    public Path dir() {
        return dir;
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
}
