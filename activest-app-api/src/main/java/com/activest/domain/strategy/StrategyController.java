package com.activest.domain.strategy;

import com.activest.config.SecurityUtils;
import com.activest.domain.strategy.dto.RebalanceDTO;
import com.activest.domain.strategy.dto.StrategyDTO;
import com.activest.domain.strategy.dto.StrategyReservationDTO;
import com.activest.domain.strategy.dto.StrategyReservationUpdateDTO;
import com.activest.domain.strategy.dto.StrategyUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {

    private final StrategyService strategyService;
    private final StrategyReservationService strategyReservationService;

    public StrategyController(StrategyService strategyService,
                              StrategyReservationService strategyReservationService) {
        this.strategyService = strategyService;
        this.strategyReservationService = strategyReservationService;
    }

    @GetMapping("/")
    public ResponseEntity<List<StrategyDTO>> getUserStrategy() {
        return ResponseEntity.ok(strategyService.getUserStrategy(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/")
    public ResponseEntity<List<StrategyDTO>> updateStrategy(@Valid @RequestBody StrategyUpdateDTO dto) {
        return ResponseEntity.ok(strategyService.updateStrategy(SecurityUtils.getCurrentUserId(), dto));
    }

    /** Estrategia da reserva de emergencia, com os numeros apurados da carteira. */
    @GetMapping("/reservation")
    public ResponseEntity<StrategyReservationDTO> getReservation() {
        return ResponseEntity.ok(strategyReservationService.get(SecurityUtils.getCurrentUserId()));
    }

    /**
     * Troca o tipo e o alvo da estrategia de reserva. Nao ha DELETE: a
     * estrategia de reserva nao pode ser apagada.
     */
    @PutMapping("/reservation")
    public ResponseEntity<StrategyReservationDTO> updateReservation(
            @Valid @RequestBody StrategyReservationUpdateDTO dto) {
        return ResponseEntity.ok(strategyReservationService.update(SecurityUtils.getCurrentUserId(), dto));
    }

    @GetMapping("/rebalance")
    public ResponseEntity<RebalanceDTO> getRebalanceSuggestion() {
        return ResponseEntity.ok(strategyService.getRebalanceSuggestion(SecurityUtils.getCurrentUserId()));
    }

}
