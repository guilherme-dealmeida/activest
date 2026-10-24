package com.activest.domain.portfolio;

import com.activest.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/** "Informar custo" da bonificação sem custo da B3 (docs/planos/PLANO_NOTIFICACOES.md, 5.4). */
@RestController
@RequestMapping("/api/v1/portfolio/assets")
@RequiredArgsConstructor
public class BonusCostController {

    private final BonusCostService service;

    public record CostRequest(BigDecimal costPerShare) {
    }

    @PutMapping("/{positionId}/stock-events/{stockEventId}/cost")
    public ResponseEntity<Void> setCost(@PathVariable UUID positionId, @PathVariable Long stockEventId,
                                        @RequestBody CostRequest body) {
        service.setCost(SecurityUtils.getCurrentUserId(), positionId, stockEventId,
            body == null ? null : body.costPerShare());
        return ResponseEntity.noContent().build();
    }
}
