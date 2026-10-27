package com.activest.domain.asset;

import com.activest.domain.asset.dto.AssetCategoryResponseDTO;
import com.activest.domain.asset.dto.AssetResponseDTO;
import com.activest.domain.asset.dto.AssetTypeResponseDTO;
import com.activest.domain.pricehistory.AssetPriceHistoryService;
import com.activest.domain.pricehistory.dto.PriceHistoryDTO;
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
    private final AssetPriceHistoryService priceHistory;

    public AssetController(AssetService assetService, AssetLogos assetLogos, AssetPriceHistoryService priceHistory) {
        this.assetService = assetService;
        this.assetLogos = assetLogos;
        this.priceHistory = priceHistory;
    }

    /**
     * Logo do ativo, guardado em carga/data/logos pela rotina de logos (F7).
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

    /**
     * Busca de ativos. Exige {@code query}: sem ela a resposta seria o catálogo inteiro (~55 mil ativos com os fundos)
     * — 400 (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, C12/D27). Com {@code typeId} do tipo fundo, a busca é a dos
     * fundos ({@code includeInactive} traz os encerrados; o cabeçalho {@code X-Inactive-Count} diz quantos casam).
     */
    @GetMapping("/")
    public ResponseEntity<List<AssetResponseDTO>> listAssets(
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean withIssuer,
            @RequestParam(required = false) com.activest.domain.issuer.IssuerKind issuerKind,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!withIssuer && assetService.isFundType(typeId)) {
            AssetService.FundSearch found = assetService.searchFunds(query, includeInactive);
            return ResponseEntity.ok().header("X-Inactive-Count", String.valueOf(found.inactiveMatches()))
                .header("Access-Control-Expose-Headers", "X-Inactive-Count").body(found.assets());
        }
        if (withIssuer) {
            // tela de emissoras: so acoes e FIIs ligados a emissor; issuerKind=COMPANY so acoes (Preco Teto)
            return ResponseEntity.ok(assetService.searchAssetsWithIssuer(query, issuerKind));
        }
        // typeId junto com query restringe o autocomplete ao tipo escolhido
        return ResponseEntity.ok(assetService.searchAssets(query, typeId));
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

    /**
     * Cotacao historica para o grafico da tela de emissoras (docs/planos/PLANO_TELA_EMISSORES.md, B5):
     * {@code range} = 1D, 7D, 1M, 6M, YTD, 1A (padrao), 5A ou MAX, ou o periodo {@code from}/{@code to}.
     * Ajustada por desdobramento, grupamento e bonificacao (D9).
     */
    @GetMapping("/{ticker}/prices")
    public ResponseEntity<PriceHistoryDTO> prices(
            @PathVariable String ticker,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(priceHistory.history(ticker, range, from, to));
    }
}
