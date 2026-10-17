package com.primevest.domain.performance;

import com.primevest.config.SecurityUtils;
import com.primevest.exception.BusinessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Rentabilidade comparativa (docs/planos/PLANO_RENTABILIDADE.md, secao 5). Parametros comuns: {@code range}
 * (1M, 6M, YTD, 1A, 5A, MAX — padrao MAX) ou {@code from}/{@code to}; recorte {@code type} (nome do tipo),
 * {@code category} (id), {@code position} (id da posicao); {@code reservation=WITH|WITHOUT}; {@code basis=NET|GROSS};
 * {@code benchmarks=CDI,IBOV,IPCA_MAIS:6} (padrao CDI). So os dados do proprio usuario.
 */
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceService service;

    public PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @GetMapping("/benchmarks")
    public ResponseEntity<PerformanceDTOs.BenchmarkCatalogDTO> benchmarks() {
        return ResponseEntity.ok(service.benchmarks());
    }

    @GetMapping("/summary")
    public ResponseEntity<PerformanceDTOs.SummaryDTO> summary(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) Long category,
            @RequestParam(required = false) UUID position, @RequestParam(required = false) String reservation,
            @RequestParam(required = false) String basis, @RequestParam(required = false) String benchmarks) {
        return ResponseEntity.ok(service.summary(SecurityUtils.getCurrentUserId(),
                request(range, from, to, type, category, position, reservation, basis, benchmarks)));
    }

    @GetMapping("/series")
    public ResponseEntity<PerformanceDTOs.SeriesDTO> series(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) Long category,
            @RequestParam(required = false) UUID position, @RequestParam(required = false) String reservation,
            @RequestParam(required = false) String basis, @RequestParam(required = false) String benchmarks) {
        return ResponseEntity.ok(service.series(SecurityUtils.getCurrentUserId(),
                request(range, from, to, type, category, position, reservation, basis, benchmarks)));
    }

    @GetMapping("/periodic")
    public ResponseEntity<PerformanceDTOs.PeriodicDTO> periodic(
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) Long category,
            @RequestParam(required = false) UUID position, @RequestParam(required = false) String reservation,
            @RequestParam(required = false) String basis, @RequestParam(required = false) String benchmarks) {
        return ResponseEntity.ok(service.periodic(SecurityUtils.getCurrentUserId(),
                request(range, from, to, type, category, position, reservation, basis, benchmarks), granularity));
    }

    @GetMapping("/table")
    public ResponseEntity<PerformanceDTOs.TableDTO> table(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) Long category,
            @RequestParam(required = false) UUID position, @RequestParam(required = false) String reservation,
            @RequestParam(required = false) String basis, @RequestParam(required = false) String benchmarks) {
        return ResponseEntity.ok(service.table(SecurityUtils.getCurrentUserId(),
                request(range, from, to, type, category, position, reservation, basis, benchmarks)));
    }

    @GetMapping("/breakdown")
    public ResponseEntity<PerformanceDTOs.BreakdownDTO> breakdown(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) Long category,
            @RequestParam(required = false) UUID position, @RequestParam(required = false) String reservation,
            @RequestParam(required = false) String basis, @RequestParam(required = false) String benchmarks) {
        return ResponseEntity.ok(service.breakdown(SecurityUtils.getCurrentUserId(),
                request(range, from, to, type, category, position, reservation, basis, benchmarks), level));
    }

    @GetMapping("/wealth")
    public ResponseEntity<PerformanceDTOs.WealthDTO> wealth(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) Long category,
            @RequestParam(required = false) UUID position, @RequestParam(required = false) String reservation,
            @RequestParam(required = false) String basis) {
        return ResponseEntity.ok(service.wealth(SecurityUtils.getCurrentUserId(),
                request(range, from, to, type, category, position, reservation, basis, null)));
    }

    @GetMapping("/risk")
    public ResponseEntity<PerformanceDTOs.RiskDTO> risk(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type, @RequestParam(required = false) Long category,
            @RequestParam(required = false) UUID position, @RequestParam(required = false) String reservation,
            @RequestParam(required = false) String basis) {
        return ResponseEntity.ok(service.risk(SecurityUtils.getCurrentUserId(),
                request(range, from, to, type, category, position, reservation, basis, null)));
    }

    static PerformanceService.Request request(String range, LocalDate from, LocalDate to, String type, Long category,
                                              UUID position, String reservation, String basis, String benchmarks) {
        PerformanceScope scope = new PerformanceScope(blank(type) ? null : type.trim().toUpperCase(Locale.ROOT),
                category, position, null, parse(ReservationFilter.class, reservation, "reserva"),
                parse(PerformanceBasis.class, basis, "base"));
        List<BenchmarkData.Ref> refs = blank(benchmarks) ? List.of()
                : Arrays.stream(benchmarks.split(",")).filter(s -> !s.isBlank()).map(BenchmarkData.Ref::parse)
                .distinct().toList();
        if (refs.size() > 8) {
            throw new BusinessException("No máximo 8 benchmarks por consulta");
        }
        return new PerformanceService.Request(scope, range, from, to, refs);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String what) {
        if (blank(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Valor inválido para " + what + ": " + raw);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
