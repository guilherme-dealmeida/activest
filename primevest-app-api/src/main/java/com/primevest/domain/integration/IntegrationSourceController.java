package com.primevest.domain.integration;

import com.primevest.domain.integration.dto.SourceDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * As fontes que o servidor tem ligadas (docs/planos/PLANO_INTEGRACAO_B3.md, F6): a Binance sempre; a B3 so
 * com {@code b3i.enabled}. A tela de Integracoes oferece so estas.
 */
@RestController
@RequestMapping("/api/v1/integrations/sources")
public class IntegrationSourceController {

    private final IntegrationService service;

    public IntegrationSourceController(IntegrationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SourceDTO>> list() {
        return ResponseEntity.ok(service.sources());
    }
}
