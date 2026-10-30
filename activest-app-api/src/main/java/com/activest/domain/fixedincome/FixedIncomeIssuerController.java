package com.activest.domain.fixedincome;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetResolver;
import com.activest.domain.asset.AssetTypeNames;
import com.activest.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Busca dos seletores de emissora e distribuidora (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 6). Catalogo
 * global, so leitura.
 *
 * <p>{@code GET /api/v1/fixed-income-issuers?role=ISSUER&ticker=CDB&q=nu} — emissora: os tipos que o produto aceita
 * (CDB so bancos); {@code role=DISTRIBUTOR}: corretoras, DTVMs, bancos, financeiras e cooperativas. "Instituicao
 * nao listada" vem sempre por ultimo (no Tesouro, a emissora e so o Tesouro Nacional).</p>
 */
@RestController
@RequestMapping("/api/v1/fixed-income-issuers")
public class FixedIncomeIssuerController {

    private final FixedIncomeIssuers issuers;
    private final AssetResolver assets;
    private final FixedIncomeLogos logos;

    public FixedIncomeIssuerController(FixedIncomeIssuers issuers, AssetResolver assets, FixedIncomeLogos logos) {
        this.issuers = issuers;
        this.assets = assets;
        this.logos = logos;
    }

    /**
     * Logo de uma empresa de renda fixa pela raiz do CNPJ, gravado em carga/data/logos/rf pela adm-api (Adendo A do
     * plano). Publico e com a mesma CSP dos logos do catalogo ({@code /assets/{ticker}/logo}): a tela usa
     * {@code <img src>}, que nao manda o token. 404 = sem logo.
     */
    @GetMapping("/logos/{cnpjRoot}")
    public ResponseEntity<org.springframework.core.io.Resource> logo(@PathVariable String cnpjRoot) {
        return logos.find(cnpjRoot)
                .map(file -> ResponseEntity.ok()
                        .contentType(file.mediaType())
                        .cacheControl(org.springframework.http.CacheControl
                                .maxAge(java.time.Duration.ofDays(7)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                        .<org.springframework.core.io.Resource>body(
                                new org.springframework.core.io.FileSystemResource(file.path())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<FixedIncomeIssuerDTO>> search(
            @RequestParam(defaultValue = "ISSUER") FixedIncomeIssuers.Role role,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "30") int limit) {
        Asset asset = null;
        if (role == FixedIncomeIssuers.Role.ISSUER) {
            if (ticker == null || ticker.isBlank()) {
                throw new BusinessException("Informe o produto (ticker) para buscar a emissora.");
            }
            // Produto de renda fixa ou Tesouro: o ticker é procurado só nesses tipos (V13)
            asset = assets.findInTypes(ticker, java.util.Set.of(AssetTypeNames.RENDA_FIXA, AssetTypeNames.TESOURO_DIRETO))
                    .orElseThrow(() -> new BusinessException("Ativo não encontrado no catálogo: " + ticker));
        }
        return ResponseEntity.ok(issuers.search(role, asset, q, limit));
    }
}
