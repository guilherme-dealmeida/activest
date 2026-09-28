package com.primevest.domain.portfolio;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.portfolio.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/")
    public ResponseEntity<PortfolioSummaryDTO> getSummary() {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/assets")
    public ResponseEntity<List<PortfolioAssetDetailDTO>> getAssets() {
        return ResponseEntity.ok(portfolioService.getPortfolioAssets(SecurityUtils.getCurrentUserId()));
    }

    /**
     * Liga ou desliga a reserva de emergência da posição.
     *
     * <p>PATCH porque muda um campo da posição, não a posição inteira, e o
     * corpo traz o estado desejado em vez de um toggle — ver
     * {@code PortfolioService.setReservation}.</p>
     */
    @PatchMapping("/assets/{id}/reservation")
    public ResponseEntity<PortfolioAssetReservationDTO> setReservation(
            @PathVariable UUID id,
            @Valid @RequestBody ReservationUpdateDTO dto) {
        return ResponseEntity.ok(portfolioService.setReservation(
            SecurityUtils.getCurrentUserId(), id, dto.isReservation()));
    }

    /**
     * Alocacao por tipo. {@code excludeReservation=true} deixa so a geracao de
     * renda (tela Alocacao); sem ele, a carteira inteira (Dashboard).
     */
    @GetMapping("/allocation")
    public ResponseEntity<List<PortfolioAllocationDTO>> getAllocation(
            @RequestParam(defaultValue = "false") boolean excludeReservation) {
        return ResponseEntity.ok(portfolioService.getAllocation(SecurityUtils.getCurrentUserId(), excludeReservation));
    }

    /** Reserva de emergencia x geracao de renda, pelo valor atual. */
    @GetMapping("/allocation/reservation")
    public ResponseEntity<ReservationAllocationDTO> getReservationAllocation() {
        return ResponseEntity.ok(portfolioService.getReservationAllocation(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/allocation/detailed")
    public ResponseEntity<List<PortfolioAllocationDetailedDTO>> getAllocationDetailed() {
        return ResponseEntity.ok(portfolioService.getAllocationDetailed(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/performance")
    public ResponseEntity<PortfolioPerformanceDTO> getPerformance() {
        return ResponseEntity.ok(portfolioService.getPerformance(SecurityUtils.getCurrentUserId()));
    }
    
    @GetMapping("/dividends")
    public ResponseEntity<Object> getDividends() {
        return ResponseEntity.ok().build();
    }
}
