package com.activest.domain.dividend;

import com.activest.config.SecurityUtils;
import com.activest.domain.dividend.dto.DividendResponseDTO;
import com.activest.domain.dividend.dto.DividendSummaryDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Proventos sao somente leitura. Todos entram pelo DividendSyncScheduler, que
 * conhece a data-com e as cotas que o usuario tinha nela; o cadastro manual
 * usava as cotas do dia do lancamento e gravava total errado.
 */
@RestController
@RequestMapping("/api/v1/dividends")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @GetMapping("/")
    public ResponseEntity<DividendSummaryDTO> summary() {
        return ResponseEntity.ok(dividendService.getDividendSummary(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/asset/{ticker}")
    public ResponseEntity<List<DividendResponseDTO>> dividendsByAsset(@PathVariable String ticker) {
        return ResponseEntity.ok(dividendService.getDividendsByAsset(SecurityUtils.getCurrentUserId(), ticker));
    }
}
