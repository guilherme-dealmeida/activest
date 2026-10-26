package com.activest.domain.goal;

import com.activest.config.SecurityUtils;
import com.activest.domain.goal.dto.GoalDTOs.*;
import com.activest.domain.goal.engine.Evaluation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Metas financeiras (docs/planos/PLANO_METAS.md, Anexo A 11). */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public ResponseEntity<CatalogDTO> types() {
        return ResponseEntity.ok(service.catalog());
    }

    @GetMapping("/scope-options")
    public ResponseEntity<ScopeOptionsDTO> scopeOptions() {
        return ResponseEntity.ok(service.scopeOptions(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/preview")
    public ResponseEntity<PreviewDTO> preview(@Valid @RequestBody GoalRequest req) {
        return ResponseEntity.ok(service.preview(SecurityUtils.getCurrentUserId(), req));
    }

    /** Valores automaticos das premissas para o formulario (sem validar o cadastro). */
    @PostMapping("/assumptions")
    public ResponseEntity<List<Evaluation.Assumption>> assumptions(@RequestBody GoalRequest req) {
        return ResponseEntity.ok(service.automaticAssumptions(SecurityUtils.getCurrentUserId(), req));
    }

    @GetMapping({"", "/"})
    public ResponseEntity<GoalListDTO> list(@RequestParam(required = false) String lifecycle,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String situation) {
        return ResponseEntity.ok(service.list(SecurityUtils.getCurrentUserId(), lifecycle, category, situation));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(SecurityUtils.getCurrentUserId(), id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<HistoryDTO> history(@PathVariable UUID id,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.history(SecurityUtils.getCurrentUserId(), id, from, to));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<EventDTO>> events(@PathVariable UUID id) {
        return ResponseEntity.ok(service.events(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<GoalDTO> create(@Valid @RequestBody GoalRequest req) {
        return ResponseEntity.ok(service.create(SecurityUtils.getCurrentUserId(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalDTO> update(@PathVariable UUID id, @Valid @RequestBody GoalRequest req) {
        return ResponseEntity.ok(service.update(SecurityUtils.getCurrentUserId(), id, req));
    }

    @PostMapping("/{id}/{action:pause|resume|complete|archive|unarchive}")
    public ResponseEntity<GoalDTO> transition(@PathVariable UUID id, @PathVariable String action) {
        return ResponseEntity.ok(service.transition(SecurityUtils.getCurrentUserId(), id, action));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
