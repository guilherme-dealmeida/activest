package com.primevest.domain.portfolio;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.portfolio.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/allocation")
    public ResponseEntity<List<PortfolioAllocationDTO>> getAllocation() {
        return ResponseEntity.ok(portfolioService.getAllocation(SecurityUtils.getCurrentUserId()));
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
