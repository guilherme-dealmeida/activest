package com.primevest.domain.ceilingprice;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.CeilingPriceInput;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.Defaults;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.Detail;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.ListItem;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.Rejection;
import com.primevest.domain.ceilingprice.dto.CeilingPriceDTOs.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Preço Teto pelo modelo PREÇO TETO V2 (docs/planos/PLANO_PRECO_TETO.md, 5.3). Um cálculo por ativo do usuário;
 * entradas gravadas, resultado calculado na leitura.
 */
@RestController
@RequestMapping("/api/v1/ceiling-prices")
public class CeilingPriceController {

    private final CeilingPriceService service;

    public CeilingPriceController(CeilingPriceService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    public List<ListItem> list() {
        return service.list(SecurityUtils.getCurrentUserId());
    }

    /** Padrões de hoje para o ticker (cálculo novo, ↻, "Restaurar todos os padrões"); traz o id se já existir. */
    @GetMapping("/defaults/{ticker}")
    public Defaults defaults(@PathVariable String ticker) {
        return service.defaults(SecurityUtils.getCurrentUserId(), ticker);
    }

    /** Calcula sem gravar (prévia enquanto o usuário digita). */
    @PostMapping("/calculate")
    public Result calculate(@RequestBody CeilingPriceInput input) {
        return service.calculate(input);
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable UUID id) {
        return service.detail(SecurityUtils.getCurrentUserId(), id);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Detail> create(@RequestBody CeilingPriceInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(SecurityUtils.getCurrentUserId(), input));
    }

    @PutMapping("/{id}")
    public Detail update(@PathVariable UUID id, @RequestBody CeilingPriceInput input) {
        return service.update(SecurityUtils.getCurrentUserId(), id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Recusa ao salvar (campos vazios, bloqueios, alertas sem confirmação): 422 com os avisos. */
    @ExceptionHandler(CeilingPriceService.Rejected.class)
    public ResponseEntity<Rejection> rejected(CeilingPriceService.Rejected e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new Rejection(e.getMessage(), e.issues()));
    }
}
