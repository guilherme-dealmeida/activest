package com.primevest.domain.integration;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.integration.dto.ConfirmImportDTO;
import com.primevest.domain.integration.dto.ConnectionCreateDTO;
import com.primevest.domain.integration.dto.ExclusionDTO;
import com.primevest.domain.integration.dto.ConnectionResponseDTO;
import com.primevest.domain.integration.dto.ImportPreviewDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Conexoes com corretoras (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.7). E o que a tela de
 * Integracoes (F6) vai chamar; ate la, pelo Swagger.
 */
@RestController
@RequestMapping("/api/v1/integrations/connections")
public class IntegrationController {

    private final IntegrationService service;

    public IntegrationController(IntegrationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ConnectionResponseDTO>> list() {
        return ResponseEntity.ok(service.list(SecurityUtils.getCurrentUserId()));
    }

    /** Confere a chave na corretora (so leitura) e grava cifrada. */
    @PostMapping
    public ResponseEntity<ConnectionResponseDTO> connect(@Valid @RequestBody ConnectionCreateDTO dto) {
        return ResponseEntity.ok(service.connect(SecurityUtils.getCurrentUserId(), dto));
    }

    /** Apaga a credencial e marca a conexao como desconectada. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        service.disconnect(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Pede uma sincronizacao: entra na fila e responde na hora (202, status
     * QUEUED). A busca roda em segundo plano; acompanhe por GET .../runs/{runId}
     * ate AWAITING_CONFIRMATION (ou FAILED). Nao grava transacao.
     */
    @PostMapping("/{id}/preview")
    public ResponseEntity<ImportPreviewDTO> preview(@PathVariable UUID id) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
            .body(service.preview(SecurityUtils.getCurrentUserId(), id));
    }

    /** Historico de sincronizacoes da conexao. */
    @GetMapping("/{id}/runs")
    public ResponseEntity<List<com.primevest.domain.integration.dto.RunSummaryDTO>> runs(@PathVariable UUID id) {
        return ResponseEntity.ok(service.runs(SecurityUtils.getCurrentUserId(), id));
    }

    /** A pre-visualizacao guardada (ou o resultado, depois de confirmada). */
    @GetMapping("/{id}/runs/{runId}")
    public ResponseEntity<ImportPreviewDTO> run(@PathVariable UUID id, @PathVariable UUID runId) {
        return ResponseEntity.ok(service.run(SecurityUtils.getCurrentUserId(), id, runId));
    }

    /**
     * Grava as operacoes novas e resolve as possiveis duplicatas como decidido
     * no corpo. Tudo ou nada.
     */
    @PostMapping("/{id}/runs/{runId}/confirm")
    public ResponseEntity<ImportPreviewDTO> confirm(@PathVariable UUID id, @PathVariable UUID runId,
                                                    @RequestBody(required = false) ConfirmImportDTO dto) {
        return ResponseEntity.ok(service.confirm(SecurityUtils.getCurrentUserId(), id, runId, dto));
    }

    /** Descarta a pre-visualizacao. */
    @PostMapping("/{id}/runs/{runId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @PathVariable UUID runId) {
        service.cancel(SecurityUtils.getCurrentUserId(), id, runId);
        return ResponseEntity.noContent().build();
    }

    /** Operacoes importadas que o usuario excluiu (nao voltam na proxima busca). */
    @GetMapping("/{id}/exclusions")
    public ResponseEntity<List<ExclusionDTO>> exclusions(@PathVariable UUID id) {
        return ResponseEntity.ok(service.exclusions(SecurityUtils.getCurrentUserId(), id));
    }

    /** Desfaz a exclusao: a operacao volta para a carteira na hora, com os valores da corretora. */
    @PostMapping("/{id}/exclusions/{exclusionId}/restore")
    public ResponseEntity<com.primevest.domain.transaction.dto.TransactionResponseDTO> restoreExclusion(
            @PathVariable UUID id, @PathVariable UUID exclusionId) {
        return ResponseEntity.ok(service.restoreExclusion(SecurityUtils.getCurrentUserId(), id, exclusionId));
    }
}
