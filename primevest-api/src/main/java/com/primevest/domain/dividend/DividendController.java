package com.primevest.domain.dividend;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.dividend.dto.DividendCreateDTO;
import com.primevest.domain.dividend.dto.DividendResponseDTO;
import com.primevest.domain.dividend.dto.DividendSummaryDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dividends")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @PostMapping("/")
    public ResponseEntity<DividendResponseDTO> registerDividend(@Valid @RequestBody DividendCreateDTO dto) {
        return ResponseEntity.ok(dividendService.registerDividend(SecurityUtils.getCurrentUserId(), dto));
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
