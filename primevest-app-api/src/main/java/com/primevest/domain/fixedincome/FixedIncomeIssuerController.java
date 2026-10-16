package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final AssetRepository assets;

    public FixedIncomeIssuerController(FixedIncomeIssuers issuers, AssetRepository assets) {
        this.issuers = issuers;
        this.assets = assets;
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
            String t = ticker.trim();
            asset = assets.findByTicker(t).or(() -> assets.findByTicker(t.toUpperCase()))
                    .orElseThrow(() -> new BusinessException("Ativo não encontrado no catálogo: " + ticker));
        }
        return ResponseEntity.ok(issuers.search(role, asset, q, limit));
    }
}
