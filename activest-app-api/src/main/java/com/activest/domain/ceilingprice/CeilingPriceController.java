package com.activest.domain.ceilingprice;

import com.activest.config.SecurityUtils;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.CeilingPriceInput;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Defaults;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Detail;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.ListItem;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Rejection;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Result;
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

    /** Coluna "Alerta" da lista: alertar preço abaixo do ideal. */
    @PatchMapping("/{id}/alert")
    public java.util.Map<String, Boolean> alert(@PathVariable UUID id,
                                                @RequestBody com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.AlertInput body) {
        return java.util.Map.of("alertEnabled",
                service.setAlert(com.activest.config.SecurityUtils.getCurrentUserId(), id, body != null && body.enabled()));
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
