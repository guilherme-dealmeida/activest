package com.primevest.domain.strategy;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.strategy.dto.RebalanceDTO;
import com.primevest.domain.strategy.dto.StrategyDTO;
import com.primevest.domain.strategy.dto.StrategyUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping("/")
    public ResponseEntity<List<StrategyDTO>> getUserStrategy() {
        return ResponseEntity.ok(strategyService.getUserStrategy(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/")
    public ResponseEntity<List<StrategyDTO>> updateStrategy(@Valid @RequestBody StrategyUpdateDTO dto) {
        return ResponseEntity.ok(strategyService.updateStrategy(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/rebalance")
    public ResponseEntity<RebalanceDTO> getRebalanceSuggestion() {
        return ResponseEntity.ok(strategyService.getRebalanceSuggestion(SecurityUtils.getCurrentUserId()));
    }
}
