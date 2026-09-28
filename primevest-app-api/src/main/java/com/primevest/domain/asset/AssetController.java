package com.primevest.domain.asset;

import com.primevest.domain.asset.dto.AssetCategoryResponseDTO;
import com.primevest.domain.asset.dto.AssetResponseDTO;
import com.primevest.domain.asset.dto.AssetTypeResponseDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetLogos assetLogos;

    public AssetController(AssetService assetService, AssetLogos assetLogos) {
        this.assetService = assetService;
        this.assetLogos = assetLogos;
    }

    /**
     * Logo do ativo, guardado em data/logos pela rotina de logos (F7).
     *
     * <p>Publico (sem token): a tela usa em {@code <img src>}, que nao envia o
     * cabecalho Authorization, e e so a marca da empresa — nada do usuario.
     * Cache longo no navegador: o arquivo so muda na atualizacao mensal.
     * 404 = sem logo; a tela mostra as iniciais.</p>
     */
    @GetMapping("/{ticker}/logo")
    public ResponseEntity<Resource> logo(@PathVariable String ticker) {
        return assetLogos.find(ticker)
            .map(file -> ResponseEntity.ok()
                .contentType(file.mediaType())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .<Resource>body(new FileSystemResource(file.path())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/")
    public ResponseEntity<List<AssetResponseDTO>> listAssets(
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String query) {
        if (query != null && !query.isEmpty()) {
            // typeId junto com query restringe o autocomplete ao tipo escolhido
            return ResponseEntity.ok(assetService.searchAssets(query, typeId));
        } else if (typeId != null) {
            return ResponseEntity.ok(assetService.getAssetsByType(typeId));
        }
        return ResponseEntity.ok(assetService.getAllAssets());
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<AssetResponseDTO> getByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(assetService.getAssetByTicker(ticker));
    }

    @GetMapping("/types")
    public ResponseEntity<List<AssetTypeResponseDTO>> listTypes() {
        return ResponseEntity.ok(assetService.getAllTypes());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<AssetCategoryResponseDTO>> listCategories(@RequestParam(required = false) Long typeId) {
        if (typeId != null) {
            return ResponseEntity.ok(assetService.getCategoriesByType(typeId));
        }
        return ResponseEntity.ok(List.of()); // Or all categories if supported
    }

    @GetMapping("/{ticker}/history")
    public ResponseEntity<Object> getPriceHistory(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        // Handled by pricehistory module, would call AssetPriceHistoryService here or redirect
        return ResponseEntity.ok().build();
    }
}
