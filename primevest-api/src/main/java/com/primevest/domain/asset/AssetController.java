package com.primevest.domain.asset;

import com.primevest.domain.asset.dto.AssetCategoryResponseDTO;
import com.primevest.domain.asset.dto.AssetResponseDTO;
import com.primevest.domain.asset.dto.AssetTypeResponseDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
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
