package com.primevest.domain.ceilingprice;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.ceilingprice.dto.CeilingPriceCreateDTO;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ceiling-prices")
public class CeilingPriceController {

    private final CeilingPriceService ceilingPriceService;

    public CeilingPriceController(CeilingPriceService ceilingPriceService) {
        this.ceilingPriceService = ceilingPriceService;
    }

    @GetMapping("/")
    public ResponseEntity<List<CeilingPriceDTO>> list() {
        return ResponseEntity.ok(ceilingPriceService.getCeilingPrices(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/")
    public ResponseEntity<CeilingPriceDTO> create(@Valid @RequestBody CeilingPriceCreateDTO dto) {
        return ResponseEntity.ok(ceilingPriceService.createCeilingPrice(SecurityUtils.getCurrentUserId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CeilingPriceDTO> update(@PathVariable UUID id, @Valid @RequestBody CeilingPriceCreateDTO dto) {
        return ResponseEntity.ok(ceilingPriceService.updateCeilingPrice(SecurityUtils.getCurrentUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ceilingPriceService.deleteCeilingPrice(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
