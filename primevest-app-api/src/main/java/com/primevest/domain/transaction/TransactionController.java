package com.primevest.domain.transaction;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.transaction.dto.NoteOptionDTO;
import com.primevest.domain.transaction.dto.TransactionCreateDTO;
import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/")
    public ResponseEntity<TransactionResponseDTO> createTransaction(@Valid @RequestBody TransactionCreateDTO dto) {
        return ResponseEntity.ok(transactionService.createTransaction(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactions() {
        return ResponseEntity.ok(transactionService.getTransactions(SecurityUtils.getCurrentUserId()));
    }

    /** Titulos de renda fixa do usuario num ativo, para o seletor da Nova Transacao (todos, inclusive liquidados). */
    @GetMapping("/notes")
    public ResponseEntity<List<NoteOptionDTO>> getNotes(@RequestParam String ticker) {
        return ResponseEntity.ok(transactionService.getNotes(SecurityUtils.getCurrentUserId(), ticker));
    }

    /**
     * Previa de uma venda de renda fixa ou Tesouro, sem gravar: bruto, IR, IOF, liquido e o disponivel na data
     * (docs/planos/PLANO_VENDA_RENDA_FIXA.md). {@code excludeId}: a venda em edicao, que fica de fora da conta.
     */
    @PostMapping("/sale-preview")
    public ResponseEntity<com.primevest.domain.transaction.dto.SalePreviewDTO> previewSale(
            @Valid @RequestBody TransactionCreateDTO dto, @RequestParam(required = false) UUID excludeId) {
        return ResponseEntity.ok(transactionService.previewSale(SecurityUtils.getCurrentUserId(), dto, excludeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> getTransactionById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getTransactionById(SecurityUtils.getCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> updateTransaction(@PathVariable UUID id, @Valid @RequestBody TransactionCreateDTO dto) {
        return ResponseEntity.ok(transactionService.updateTransaction(SecurityUtils.getCurrentUserId(), id, dto));
    }

    /** Regra E4: volta a importada aos valores da corretora. */
    @PostMapping("/{id}/restore")
    public ResponseEntity<TransactionResponseDTO> restoreFromSource(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.restoreFromSource(SecurityUtils.getCurrentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        transactionService.deleteTransaction(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
