package com.primevest.domain.asset;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Logos gravados em {@code carga/data/logos/} (docs/planos/PLANO_BRAPI.md, B18): quais tipos tem logo, quem tem
 * arquivo e qual arquivo servir. Separado do download ({@code AssetLogoService}, adm-api), que e
 * rotina de ingestao (docs/planos/PLANO_ADM_API.md, 4.3).
 *
 * <p><b>O arquivo e o estado</b> (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 2.3 e F10): nao ha coluna no
 * catalogo. {@code {TICKER}.{svg|png|jpg|webp}} existe → o ativo tem logo. A pasta e indexada em
 * memoria (ticker → arquivo) na primeira leitura e de novo quando o indice passa de
 * {@link #REFRESH_MILLIS} — a app-api le a pasta que a adm-api grava, em outro processo;
 * quem grava na mesma JVM atualiza o indice na hora ({@link #stored}).</p>
 */
@Slf4j
@Component
public class AssetLogos {

    private static final Set<String> TYPES_WITH_LOGO = Set.of("ACAO", "FII", "ETF", "BDR", "CRIPTO");
    private static final Map<String, MediaType> TYPES_BY_EXTENSION = Map.of(
            "svg", MediaType.valueOf("image/svg+xml"),
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "webp", MediaType.valueOf("image/webp"));
    /** A pasta tem ~1.500 arquivos: listar leva milissegundos. */
    static final long REFRESH_MILLIS = 60_000;

    private final Path dir;
    private volatile Map<String, String> index = Map.of();
    private volatile long indexedAt;

    public AssetLogos(@Value("${primevest.logos.dir:../carga/data/logos}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    /** O tipo tem logo (renda fixa, Tesouro e fundos nao tem). */
    public static boolean typeHasLogo(AssetType type) {
        return type != null && TYPES_WITH_LOGO.contains(type.getName());
    }

    /** Nome do arquivo sem extensao: o ticker em maiusculas, so letras, numeros e {@code . _ -}. */
    public static String fileStem(String ticker) {
        return ticker.trim().toUpperCase().replaceAll("[^A-Z0-9._-]", "_");
    }

    public Path dir() {
        return dir;
    }

    /** O ativo tem arquivo de logo. */
    public boolean hasLogo(String ticker) {
        return ticker != null && index().containsKey(fileStem(ticker));
    }

    /**
     * Para a tela: {@code null} quando o tipo nao tem logo (renda fixa), senao se ha arquivo.
     * E o booleano {@code hasLogo} dos DTOs.
     */
    public Boolean logoFlag(Asset asset) {
        return typeHasLogo(asset.getAssetType()) ? hasLogo(asset.getTicker()) : null;
    }

    /** O arquivo do logo de um ativo, para a API servir. */
    public Optional<LogoFile> find(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(fileStem(ticker)))
                .map(dir::resolve)
                .filter(p -> p.normalize().startsWith(dir) && Files.isRegularFile(p))
                .map(p -> new LogoFile(p, TYPES_BY_EXTENSION.getOrDefault(extension(p.getFileName().toString()),
                        MediaType.APPLICATION_OCTET_STREAM)));
    }

    /**
     * Quem gravou (ou apagou) um logo nesta JVM avisa: o indice muda na hora. {@code fileName}
     * nulo = o ativo ficou sem logo.
     */
    public void stored(String ticker, String fileName) {
        Map<String, String> copy = new ConcurrentHashMap<>(index());
        if (fileName == null) {
            copy.remove(fileStem(ticker));
        } else {
            copy.put(fileStem(ticker), fileName);
        }
        index = copy;
    }

    /** Arquivos do mesmo ticker com outra extensao (sobra de troca de logo). */
    public List<Path> otherFiles(String ticker, String keep) {
        String stem = fileStem(ticker);
        return TYPES_BY_EXTENSION.keySet().stream()
                .map(ext -> stem + "." + ext)
                .filter(name -> !name.equals(keep))
                .map(dir::resolve)
                .filter(Files::isRegularFile)
                .toList();
    }

    /** Relê a pasta agora. */
    public synchronized int reindex() {
        Map<String, String> fresh = new ConcurrentHashMap<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString())
                        .filter(name -> TYPES_BY_EXTENSION.containsKey(extension(name)))
                        .forEach(name -> fresh.putIfAbsent(name.substring(0, name.lastIndexOf('.')).toUpperCase(),
                                name));
            } catch (IOException e) {
                log.warn("Logos: nao foi possivel ler a pasta {}: {}", dir, e.getMessage());
                return index.size();
            }
        }
        index = fresh;
        indexedAt = System.currentTimeMillis();
        return fresh.size();
    }

    private Map<String, String> index() {
        if (System.currentTimeMillis() - indexedAt > REFRESH_MILLIS) {
            reindex();
        }
        return index;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    public record LogoFile(Path path, MediaType mediaType) {
    }
}
